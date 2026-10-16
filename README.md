# anzu

> **Work in progress.** Not deployed to any environment. The API contract is at stability level
> `draft` and may still change. Infrastructure, Helm charts and the build/deploy pipeline live in a
> separate repository.

Serves NeTEx validation reports to the organisations that delivered the data.

An import runs several validation stages, each producing its own report, so the unit of access is
the whole import rather than a single run:

```
GET /validation-reports/{correlationId}
GET /validation-reports/{correlationId}/{stage}
```

The codespace is resolved from the correlation id, never supplied by the caller.

## How it works

Two halves sharing one table.

**Ingest** subscribes to antu's validation status queue and appends each status event to
`validation_event`. Runs are folded from their events on read, so arrival order never matters and a
redelivered event cannot corrupt a run. Report bodies are never stored — only that a run happened
and where its report lives.

**Query** looks up the runs for a correlation id, authorizes against the codespace on the earliest
run, then reads each report blob from antu's bucket and maps it to this service's own response type.

The contract is hand-written in [`src/main/resources/openapi/validation-reports.yaml`](src/main/resources/openapi/validation-reports.yaml).
No code is generated from it.

## Build

```bash
mvn clean verify
```

Requires Java 25 and a running Docker daemon — the tests use Testcontainers for Postgres and the
Pub/Sub emulator.

## Running locally

There is no `application.properties` in the repository. Configuration is supplied externally, so
nothing environment-specific is version controlled. Point the application at your own copy with a VM
argument:

```
-Dspring.config.location=/path/to/your/application.properties
```

You will need a Postgres database, the Pub/Sub emulator (below), and a bearer token
(see [Authenticating locally](#authenticating-locally)).

```properties
spring.profiles.active=local

# Postgres. Flyway creates the schema on startup.
spring.datasource.url=jdbc:postgresql://localhost:5432/anzu
spring.datasource.username=<your-db-user>
spring.datasource.password=<your-db-password>

# Make sure the project-id in your dev environment matches the project id
# that your local antu is publishing validation status messages to, otherwise
# anzu will not be able to pick up messages from antu.
spring.cloud.gcp.project-id=test
spring.cloud.gcp.pubsub.emulator-host=localhost:8085
anzu.pubsub.enabled=true
anzu.pubsub.validation-status-topic=AntuNetexValidationStatusQueue
anzu.pubsub.validation-status-subscription=anzu-validation-status-subscription

# Reports are read from <local-path>/<report-container>/reports/, which is where antu
# writes them locally. Nothing is copied and no bucket is contacted.
anzu.blobstore.local-path=/path/to/your/blobstore
anzu.blobstore.report-container=antu

# Which issuer's tokens this instance accepts. These are the dev values, so a token obtained
# from dev works against a locally running instance.
anzu.oauth2.resourceserver.partner.jwt.issuer-uri=https://partner.dev.entur.org/
anzu.oauth2.resourceserver.partner.jwt.audience=https://api.dev.entur.io

# Grants editRouteData for any codespace, so baba is not called.
anzu.security.authorization-service=local
anzu.security.role-assignment-extractor=jwt
```

The `local` profile binds the blob store to local disk and the emulator host binds Pub/Sub, so a
local run reaches no GCP project, no bucket, nor baba.

`role-assignment-extractor` has to be set away from its `baba` default, or anzu builds a
machine-to-machine client at startup and fails on the missing credentials.

### Google Pub/Sub emulator

See the [Google Pub/Sub emulator documentation](https://cloud.google.com/pubsub/docs/emulator) for
installation. Start it with:

```bash
gcloud beta emulators pubsub start
```

It listens on port 8085 by default. The topic and subscription are created on startup.

### Authenticating locally

**Authentication is never bypassed**, including locally. Every endpoint except
`/actuator/health/**` and `/actuator/prometheus` needs a valid bearer token.

Call the API with two headers:

```
Authorization: Bearer <token>
ET-Client-Name: <your client name>
```

**Authorization** is what `anzu.security.authorization-service=local` takes care of: it grants
`editRouteData` for any codespace and calls nothing. Set it to `token-based` only if you have baba
reachable and configured.
