#!/usr/bin/env bash
# =============================================================================
#  run-sonar.sh — build both modules (with coverage) and run a single
#  SonarQube analysis covering the whole HIMS Clinical monorepo.
#
#  Usage:
#     export SONAR_TOKEN=<token from SonarQube > My Account > Security>
#     ./run-sonar.sh                       # scans http://localhost:9000
#     SONAR_HOST_URL=http://host:9000 ./run-sonar.sh
#
#  Prerequisites:
#     • A running SonarQube server (see docker-compose.sonarqube.yml)
#     • JDK 21, Node.js, and either the `sonar-scanner` CLI on PATH
#       or Docker (the script falls back to the official scanner image).
# =============================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SONAR_HOST_URL="${SONAR_HOST_URL:-http://localhost:9000}"

cd "$ROOT_DIR"

echo "==> [1/4] Backend: compile, test, JaCoCo coverage"
( cd backend && ./gradlew --no-daemon classes testClasses test jacocoTestReport )

echo "==> [2/4] Backend: resolve dependency jars for precise Java analysis"
# A throwaway init script prints the runtime classpath without touching
# build.gradle.kts. Best-effort: analysis still runs if this is skipped.
INIT_GRADLE="$(mktemp)"
cat > "$INIT_GRADLE" <<'GRADLE'
allprojects {
    tasks.register("printSonarLibraries") {
        doLast {
            val cp = configurations.findByName("runtimeClasspath")
            if (cp != null) println("SONAR_LIBS=" + cp.files.joinToString(",") { it.absolutePath })
        }
    }
}
GRADLE
SONAR_LIBS="$(cd backend && ./gradlew --no-daemon -q --init-script "$INIT_GRADLE" printSonarLibraries 2>/dev/null \
              | sed -n 's/^SONAR_LIBS=//p' | tail -1 || true)"
rm -f "$INIT_GRADLE"

echo "==> [3/4] Frontend: install deps + Vitest coverage (LCOV)"
( cd frontend && (npm ci --legacy-peer-deps || npm install --legacy-peer-deps) && npm run test:coverage )

echo "==> [4/4] Running SonarScanner"
SCANNER_ARGS=( "-Dsonar.host.url=${SONAR_HOST_URL}" )
[ -n "${SONAR_TOKEN:-}" ] && SCANNER_ARGS+=( "-Dsonar.token=${SONAR_TOKEN}" )
[ -n "${SONAR_LIBS:-}" ]  && SCANNER_ARGS+=( "-Dsonar.java.libraries=${SONAR_LIBS}" )

if command -v sonar-scanner >/dev/null 2>&1; then
    sonar-scanner "${SCANNER_ARGS[@]}"
else
    echo "    sonar-scanner not found on PATH — falling back to the Docker image."
    # Inside the container, localhost is the container itself, so remap to the host.
    DOCKER_HOST_URL="${SONAR_HOST_URL/localhost/host.docker.internal}"
    docker run --rm \
        --add-host=host.docker.internal:host-gateway \
        -v "$ROOT_DIR:/usr/src" \
        ${SONAR_TOKEN:+-e SONAR_TOKEN="$SONAR_TOKEN"} \
        sonarsource/sonar-scanner-cli \
        "-Dsonar.host.url=${DOCKER_HOST_URL}" \
        ${SONAR_LIBS:+-Dsonar.java.libraries="$SONAR_LIBS"}
fi

echo "==> Done. View results at ${SONAR_HOST_URL}/dashboard?id=hims-clinical-multitenant"
