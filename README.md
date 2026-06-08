# HMS — Hospital Management System

A modern, full-stack greenfield rebuild of a legacy Hospital Management System.

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.3 / Java 21 / Gradle |
| Database | PostgreSQL 16 |
| ORM | Hibernate 6 / Spring Data JPA |
| Migrations | Flyway |
| Security | Spring Security (session-based) |
| Frontend | React 18 / TypeScript / Vite |
| State | TanStack Query v5 + Zustand |
| Styling | Tailwind CSS v3 |
| Validation | Zod + React Hook Form |

## Quick Start (Docker)

```bash
# 1. Clone and enter
git clone <repo> && cd hms-full

# 2. Start all services
docker compose up -d

# 3. Backend API
open http://localhost:8080/api/swagger-ui.html

# 4. Frontend
open http://localhost:5173
```

## Local Development

### Quick start (recommended)

`dev.sh` starts the database, backend, and frontend together. Press `Ctrl+C` to
stop all of them.

```bash
./dev.sh            # db (if needed) + backend + frontend
./dev.sh --no-db    # skip the database step (use an already-running Postgres)
./dev.sh --backend  # backend only
./dev.sh --frontend # frontend only
```

The script auto-detects a Homebrew-installed JDK and a running local Postgres,
installs frontend dependencies on first run, and falls back to `docker compose`
for the database when one isn't already listening on port 5432.

Once it's up:
- Frontend → http://localhost:5173
- Backend API → http://localhost:8080/api (Swagger at `/api/swagger-ui.html`)

Default login: **`superadmin`** / **`password`** (seeded by Flyway; SUPERADMIN
bypasses all RBAC checks).

### One-time prerequisites

```bash
brew install openjdk@21        # backend needs JDK 21
createdb -O hms_user hms_db    # if using a local Postgres (Flyway builds the schema on first boot)
```

Need the `hms_user` role too? `psql -d postgres -c "CREATE ROLE hms_user LOGIN PASSWORD 'hms_pass';"`

### Manual (run each piece yourself)

#### Backend
```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=dev'
```

#### Frontend
```bash
cd frontend
npm install
npm run dev
```

## Architecture

The project follows Hexagonal Architecture (Ports & Adapters):

```
domain/          → Pure business logic, no framework dependencies
application/     → Use case orchestration (@Service)
infrastructure/  → JPA, adapters, external services
api/             → REST controllers (thin I/O layer)
```

## Flyway Migrations

| Version | Description |
|---|---|
| V001 | Base schema: users, roles, features, departments |
| V002 | Billing: bills, charges, payments, discounts |
| V003 | Clinical: patients, encounters, beds, appointments |
| V004 | Inventory: batches, purchase receipts, pharmacy |
| V005 | Diagnostics: orders and results |

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `hms_db` | Database name |
| `DB_USER` | `hms_user` | DB username |
| `DB_PASSWORD` | `hms_pass` | DB password |
| `TWILIO_ACCOUNT_SID` | — | SMS provider |
| `SPRING_PROFILES_ACTIVE` | `dev` | Spring profile |
