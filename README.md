# careround-care-task-service

Care protocols, care plans and the care tasks they generate, including medication tasks. HTTP 8086, gRPC 9094,
DB `careround_care_task_db`.

- **REST:** `/api/v1/care-protocols`, `/api/v1/care-plans`, `/api/v1/care-tasks`
  (`/{id}/start`, `/complete`, `/cancel`, `/refuse`), `/api/v1/care-tasks/{careTaskId}/medication-details`,
  `/api/v1/task-type-definitions`.
- **Events (outbox → Kafka):** `MEDICATION_ADMINISTERED` when a task with medication details is completed.
- **gRPC server (9094):** `CareTaskQueryService` (pending task ids per patient, used by round-service).
- **Scheduled:** `CarePlanScheduler` (generates tasks on each plan's cadence), outbox publisher.
- A patient-service gRPC client address is configured but not used by any code yet.

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
