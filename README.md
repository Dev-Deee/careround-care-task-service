# careround-care-task-service

Care protocols, care plans and the care tasks they generate, including medication tasks. HTTP 8086, gRPC 9094,
DB `careround_care_task_db`.

- **REST:** `/api/v1/care-protocols`, `/api/v1/care-plans`, `/api/v1/care-tasks`
  (`/{id}/start`, `/complete`, `/cancel`, `/refuse`), `/api/v1/care-tasks/{careTaskId}/medication-details`,
  `/api/v1/task-type-definitions`.
- **Events (outbox → Kafka):** `MEDICATION_ADMINISTERED` when a task with medication details is completed.
- **gRPC server (9094):** `CareTaskQueryService` (pending task ids per patient, used by round-service).
- **gRPC clients:** patient-service `PatientExists` (9090; task/plan create → unknown patient 404,
  unreachable 503), ward-service `IsActiveChargeNurse` (9093; stats authorisation, unreachable 503).
- **Scheduled:** `CarePlanScheduler` (generates tasks on each plan's cadence), outbox publisher.

## Read side (lists, aggregates)

All reads need a valid JWT. Paged lists use `page` (0-based) and `size` (default 20, max 100; larger is clamped)
and return `{items, page, size, total}`. Malformed ids/enums/instants → 400.

| Endpoint | Who | Notes |
|---|---|---|
| `GET /api/v1/care-tasks?wardId&patientId&assignedNurseId&mine&unassigned&status&priority&overdueDerived&page&size` | any authenticated | At least one of `wardId`, `patientId`, `assignedNurseId`, `mine=true` (400 otherwise). `mine=true` = assigned to the caller. `RETRACTED` only with `status=RETRACTED`. Sort `deadline` asc, `createdAt`, `id`. |
| `GET /api/v1/care-tasks/medication-log?patientId&page&size` | DOCTOR | The caller's own medication orders (tasks with medication details) for the patient: drug/dose/route, `status`, `administeredAt` (= `completedAt`), `administeredByNurseId`, `overdueDerived`. Sort `deadline` desc. |
| `GET /api/v1/care-tasks/stats?wardId&from&to` | ADMIN, or the ward's active charge nurse | `from`/`to` ISO instants, default last 24h, `from < to`. `total`, `byStatus`, `byPriority`, `completionRate` (COMPLETED / total, null if 0) cover tasks with `deadline` in `[from, to)`, RETRACTED excluded. `overdueDerived` and `oldestOverdueAgeSeconds` are the ward's *current* open-and-late tasks, regardless of window. Other roles / non-charge nurses 403; ward-service down 503 for nurses (admins are not checked). |

**Derived overdue.** `overdueDerived` = `deadline < now` and status `PENDING` or `IN_PROGRESS`, computed at read
time. Nothing stores it; the `OVERDUE` status value exists in the enum but nothing sets it (the overdue state
machine is not built yet), so `byStatus.OVERDUE` is always 0 today.

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
