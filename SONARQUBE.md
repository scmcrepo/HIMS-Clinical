# SonarQube Integration

This repository is configured as a **single SonarQube project** that analyses
both modules together:

| Module     | Language    | Build      | Coverage source                                   |
|------------|-------------|------------|---------------------------------------------------|
| `backend`  | Java 21     | Gradle     | JaCoCo → `backend/build/reports/jacoco/test/jacocoTestReport.xml` |
| `frontend` | TypeScript  | Vite/Vitest| LCOV → `frontend/coverage/lcov.info`              |

Project key: **`hims-clinical-multitenant`**

## What was added (only SonarQube wiring)

- `sonar-project.properties` — the single-project definition (sources, tests, coverage paths, exclusions).
- `docker-compose.sonarqube.yml` — a local SonarQube server + its own DB (kept separate from the app stack).
- `run-sonar.sh` — builds both modules with coverage and runs one analysis.
- `backend/build.gradle.kts` — added the `jacoco` plugin and an XML coverage report.
- `frontend/vite.config.ts` — added the `lcov` coverage reporter.

No application code or other configuration was changed.

## 1. Start a SonarQube server

```bash
docker compose -f docker-compose.sonarqube.yml up -d
```

Open http://localhost:9000 (default login `admin` / `admin`, you'll be asked to
change the password). If the container fails to boot, raise the host limit:

```bash
sudo sysctl -w vm.max_map_count=524288
```

## 2. Create a token

In SonarQube: **My Account → Security → Generate Token**, then:

```bash
export SONAR_TOKEN=<your-token>
```

## 3. Run the analysis

```bash
./run-sonar.sh
```

This will:
1. Compile and test the backend, producing the JaCoCo XML report.
2. Run the frontend test suite with coverage, producing `lcov.info`.
3. Invoke the scanner once for the whole repo.

Results: http://localhost:9000/dashboard?id=hims-clinical-multitenant

## Running the pieces manually

```bash
# Backend coverage
cd backend && ./gradlew test jacocoTestReport

# Frontend coverage
cd frontend && npm install && npm run test:coverage

# Scan (from repo root, with a sonar-scanner CLI installed)
sonar-scanner -Dsonar.host.url=http://localhost:9000 -Dsonar.token=$SONAR_TOKEN
```

## CI note

In a pipeline, run the same three steps. The scanner reads every setting from
`sonar-project.properties`; only `sonar.host.url` and `sonar.token` need to be
supplied as environment/CLI arguments.
