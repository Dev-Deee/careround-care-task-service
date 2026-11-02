# careround-care-task-service

Care protocols, care plans and the care tasks they generate, including medication tasks. HTTP 8086, gRPC 9094,
DB `careround_care_task_db`.

- **REST:** `/api/v1/care-protocols`, `/api/v1/care-plans`, `/api/v1/care-tasks`
  (`/{id}/start`, `/complete`, `/cancel`, `/refuse`), `/api/v1/care-tasks/{careTaskId}/medication-details`,
  `/api/v1/task-type-definitions`.
- **Events (outbox → Kafka):** `MEDICATION_ADMINISTERED` when a medication task is completed (on time or late);
  `MEDICATION_OVERDUE` / `TASK_OVERDUE` when the overdue timer fires (see below).
- **gRPC server (9094):** `CareTaskQueryService` (pending task ids per patient, used by round-service).
- **gRPC clients:** patient-service `PatientExists` (9090; task/plan create → unknown patient 404,
  unreachable 503), ward-service `IsActiveChargeNurse` (9093; stats authorisation, unreachable 503).
- **Scheduled:** `CarePlanScheduler` (generates tasks on each plan's cadence), outbox publisher, `OverdueWorker`
  (overdue timer poll + reconciliation). Three scheduler threads (`spring.task.scheduling.pool.size`).
- **Redis** (`careround-redis`, 6379): the overdue timer index only. Postgres is the truth; Redis can be wiped.

## Read side (lists, aggregates)

All reads need a valid JWT. Paged lists use `page` (0-based) and `size` (default 20, max 100; larger is clamped)
and return `{items, page, size, total}`. Malformed ids/enums/instants → 400.

| Endpoint | Who | Notes |
|---|---|---|
| `GET /api/v1/care-tasks?wardId&patientId&assignedNurseId&mine&unassigned&status&priorityLevel&priority&overdueDerived&page&size` | any authenticated | At least one of `wardId`, `patientId`, `assignedNurseId`, `mine=true` (400 otherwise). `mine=true` = assigned to the caller. `RETRACTED` only with `status=RETRACTED`. Sort `deadline` asc, `createdAt`, `id`. |
| `GET /api/v1/care-tasks/medication-log?patientId&page&size` | DOCTOR | The caller's own medication orders (tasks with medication details) for the patient: drug/dose/route, `status`, `administeredAt` (= `completedAt`), `administeredByNurseId`, `overdueDerived`. Sort `deadline` desc. |
| `GET /api/v1/care-tasks/stats?wardId&from&to` | ADMIN, or the ward's active charge nurse | `from`/`to` ISO instants, defaults `now − 24h` / `now + 24h` (so tasks due later today count), `from < to`. `total`, `byStatus`, `byPriority`, `completionRate` (COMPLETED / total, null if 0) cover tasks with `deadline` in `[from, to)`, RETRACTED excluded. `overdueDerived` and `oldestOverdueAgeSeconds` are the ward's *current* open-and-late tasks, regardless of window. Other roles / non-charge nurses 403; ward-service down 503 for nurses (admins are not checked). |

**Derived overdue.** `overdueDerived` = status `OVERDUE`, or `PENDING`/`IN_PROGRESS` with `deadline < now` (a
timer that has not fired yet, at most a poll interval). `status=OVERDUE` filters the stored state. Stats'
`overdueDerived` / `oldestOverdueAgeSeconds` use the same rule.

**Task fields added (additive):** `wardId` (the patient's ward at creation, from patient-service), `createdAt`,
`completedAt`, `overdueDerived`, `taskTypeId`, `carePlanPublicId`. Medication details add `careTaskPublicId`;
the `MEDICATION_ADMINISTERED` payload adds `careTaskPublicId`.

**Deprecated (kept, still populated):** `CareTaskResponse.carePlanId` (internal plan id for scheduler tasks, or
whatever a client sent) → use `carePlanPublicId`. `MedicationDetailsResponse.careTaskId` and the
`MEDICATION_ADMINISTERED` payload's `careTaskId` (internal task id) → use `careTaskPublicId`. Create requests
take the plan's **publicId** in `carePlanId` (an unknown plan → 404).

**PRD path → built path.**

| PRD | Built |
|---|---|
| `GET /wards/{wardId}/care-tasks` | `GET /api/v1/care-tasks?wardId=` |
| `GET /patients/{id}/care-tasks` | `GET /api/v1/care-tasks?patientId=` |
| — (nurse's own work list) | `GET /api/v1/care-tasks?mine=true` |
| FR-12.4 per-patient medication log (no path in PRD) | `GET /api/v1/care-tasks/medication-log?patientId=` |
| — (ward supervision aggregate) | `GET /api/v1/care-tasks/stats?wardId=` |

**Rows created before these columns** have `ward_id`, `created_at`, `care_plan_public_id` null: they do not
appear in `wardId` lists or stats. One-off backfill (not run):

```sql
-- careround_care_task_db: plan publicIds for scheduler-generated tasks
UPDATE care_tasks t SET care_plan_public_id = p.public_id
  FROM care_plans p WHERE t.care_plan_id = p.id AND t.care_plan_public_id IS NULL;
-- ward_id needs patient data from careround_patient_db; export (public_id, ward_id) from patients,
-- load it into a temp table here, then:
UPDATE care_tasks t SET ward_id = x.ward_id FROM tmp_patient_wards x
  WHERE t.patient_id = x.public_id AND t.ward_id IS NULL;
```

## Priority

`priorityLevel` (`LOW`, `MEDIUM`, `HIGH`) is the field to use. The int `priority` is **deprecated** but still
accepted and returned: 1 or less → LOW, 2 → MEDIUM, 3 or more → HIGH. The level is **derived from the stored
int** (no column, nothing to backfill); a request with only `priorityLevel` stores 1 / 2 / 3. Sending neither, or
both disagreeing (e.g. `priority: 1, priorityLevel: HIGH`) → 400. The same applies to task types
(`defaultPriority` / `defaultPriorityLevel`). Responses add `priorityLevel` / `defaultPriorityLevel`; the list
takes `priorityLevel`; stats add `byPriorityLevel` (`byPriority` deprecated).

**Medication task types should be configured with `defaultPriorityLevel: HIGH`** (FR-2.3, "medication defaults
high"). Nothing forces it. `MEDICATION_OVERDUE` does not depend on priority.

## OVERDUE (PRD §8.3)

`OVERDUE` is a stored state, set by a per-task timer:

- **Queue.** Redis ZSET `careround:task-deadlines` (`app.overdue.queue-key`): member = task `publicId`, score =
  deadline epoch-ms. `ZADD` after commit on create and care-plan generation; `ZREM` after commit on complete,
  cancel, refuse and retract. Queue errors only log WARN (task writes never fail because Redis is down).
- **Worker.** Every `app.overdue.poll-ms` (2000) it reads due members (`ZRANGEBYSCORE -inf now LIMIT 0
  app.overdue.batch-size`) and claims each with `ZREM`; only the instance that removed it fires it, so several
  instances are safe. Firing is one transaction with a row lock: a task still `PENDING`/`IN_PROGRESS` whose deadline
  has passed becomes `OVERDUE` with `overdueAt`, and its outbox event is written; anything else is a no-op. If the
  transaction fails the member is re-queued at now + `app.overdue.retry-backoff-ms` (30 s).
- **Reconciliation.** At startup and every `app.overdue.reconcile-ms` (300000): every open task is re-`ZADD`ed, and
  open tasks already past their deadline are fired straight from Postgres (works with Redis down). Open tasks late
  by more than `app.overdue.backfill-max-age-hours` (24) become `OVERDUE` **without** events; the count is logged at
  INFO (first run against old data).
- **Transitions.** Only `PENDING → OVERDUE` and `IN_PROGRESS → OVERDUE`. An `OVERDUE` task can still be started
  (claimed if unassigned; it **stays** `OVERDUE`) and completed by its assigned nurse; late medication still
  publishes `MEDICATION_ADMINISTERED`. Cancel is unchanged (allowed); refuse is unchanged (`PENDING`/`IN_PROGRESS`
  only, so an `OVERDUE` task cannot be refused). `GetPendingTaskIds` (round-service's acknowledge gate) includes
  `OVERDUE` tasks.

**Events** (outbox, same transaction as the flip; Kafka record key = idempotency key):

| Event | When | Key | Payload |
|---|---|---|---|
| `MEDICATION_OVERDUE` | the task is medication | `medication-overdue-{taskId}` | `taskPublicId, patientId, wardId (nullable), prescribedBy, priority (level), deadline, occurredAt` |
| `TASK_OVERDUE` | not medication and `priorityLevel = HIGH` | `task-overdue-{taskId}` | `taskPublicId, patientId, wardId, type (task type name), priority, deadline, occurredAt` |
| — | anything else | — | none: passive dashboard flag only (FR-2.7) |
| `MEDICATION_ADMINISTERED` (existing) | a medication task completes | `medication-administered-{taskId}` | unchanged, plus `wardId` and `occurredAt` (= `completedAt`) |

**Medication rule** (`MedicationRule.isMedication`, shared by both events): `origin = DOCTOR_ORDER` **and** a
`MedicationDetails` row exists, decided when the event is built (details attached after creation but before the
deadline count). A doctor order without details is a normal task: `TASK_OVERDUE` if HIGH, otherwise nothing.

## Errors

Every error uses the body `{"status", "error", "message", "timestamp"}`.

- A path no endpoint maps (e.g. a collection root with a trailing slash, or a wrong sub-path) → **404**
  `No endpoint <METHOD> <path>`. A wrong HTTP method on a mapped path → 405, unsupported content type → 415,
  malformed JSON → 400.
- Unknown patient on task/plan create → 404; patient-service or ward-service unreachable → 503.
- Anything unexpected → 500 with a generic message; the exception is logged at ERROR with its stack trace.

## Environment variables

Copy `.env.example` to `.env` (gitignored) and fill in real values. `main()` loads `.env` into system properties;
plain environment variables work too.

| Variable | Purpose |
|---|---|
| `DB_USERNAME` | Postgres user (root docker-compose: careround-postgres) |
| `DB_PASSWORD` | Postgres password |
| `JWT_PUBLIC_KEY` | RSA public key used to verify access tokens; same value in every service. PEM on one line, `|` for newlines |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka (root docker-compose: careround-kafka) |

## Run

Needs the root `docker-compose.yml` stack (Postgres, Kafka, Redis) and a `.env`.

```bash
./mvnw clean package -DskipTests && java -jar target/careround-care-task-service-0.0.1-SNAPSHOT.jar
```

Clients reach the REST API through the gateway on `http://localhost:8080`; the service port also works directly.
