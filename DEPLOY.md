# Deploying a live LootLedger demo

The demo runs on **PostgreSQL alone** (no Kafka/Redis needed) via the `demo` Spring profile, so it
fits comfortably on free tiers. The interactive dashboard is served at `/` and Swagger at
`/swagger-ui.html`.

## Option A — Local, prod-like (one command)

```bash
docker compose -f docker-compose.demo.yml up --build
# open http://localhost:8080
```

This builds the app image and runs it against a bundled Postgres.

## Option B — Render.com (free, public URL)

A blueprint is included (`render.yaml`): it provisions a managed Postgres and a Docker web service,
wiring the database credentials automatically.

1. Push this repo to GitHub (already done if you're reading this there).
2. In Render, click **New → Blueprint**, select this repository, and apply.
3. Render builds the `Dockerfile`, provisions `lootledger-db`, and deploys.
4. Open the service URL — the dashboard loads at `/`.

No secrets to paste: the DB env vars are injected from the managed database via `fromDatabase`.

## Option C — Any Docker host (Railway, Fly.io, Cloud Run, a VPS)

Build and run the image, pointing it at any Postgres:

```bash
docker build -t lootledger .
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=demo \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://<host>:5432/<db>" \
  -e SPRING_DATASOURCE_USERNAME="<user>" \
  -e SPRING_DATASOURCE_PASSWORD="<pass>" \
  lootledger
```

- **Railway / Fly.io**: add a Postgres plugin/app, then set the same `SPRING_DATASOURCE_*` env vars
  (Fly: `fly launch` detects the Dockerfile; add `fly postgres create` and attach).
- **Cloud Run**: `gcloud run deploy --source .` with a Cloud SQL Postgres and the env vars above.

## Running the full stack (with Kafka + Redis)

To demo loot streaming and the outbox relay too, drop the `demo` profile and use the full compose:

```bash
docker compose up -d           # postgres, kafka, redis
./gradlew bootRun              # default profile: Kafka + Redis enabled
```

## Verifying a running instance

```bash
BASE=https://your-demo-url ./demo.sh     # seeds, benchmarks, runs a dupe storm, reconciles
```

The dashboard's **"Try to dupe the economy"** button does the same thing from the browser.
