#!/usr/bin/env bash
#
# dev.sh — start the HMS stack for local development.
#
# Brings up the PostgreSQL container (if Docker is available), then runs the
# Spring Boot backend and the React/Vite frontend together. Ctrl+C stops both.
#
# Usage:
#   ./dev.sh            # db (if needed) + backend + frontend
#   ./dev.sh --no-db    # skip the database step (use an already-running Postgres)
#   ./dev.sh --backend  # backend only
#   ./dev.sh --frontend # frontend only
#
set -euo pipefail
set -m   # job control: each background service gets its own process group

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"

# ── Config (override via env) ───────────────────────────────────────────────
export DB_HOST="${DB_HOST:-localhost}"
export DB_PORT="${DB_PORT:-5432}"
export DB_NAME="${DB_NAME:-hms_db}"
export DB_USER="${DB_USER:-hms_user}"
export DB_PASSWORD="${DB_PASSWORD:-hms_pass}"
SPRING_PROFILE="${SPRING_PROFILES_ACTIVE:-dev}"

START_DB=1
START_BACKEND=1
START_FRONTEND=1

for arg in "$@"; do
  case "$arg" in
    --no-db)     START_DB=0 ;;
    --backend)   START_DB=0; START_FRONTEND=0 ;;
    --frontend)  START_DB=0; START_BACKEND=0 ;;
    -h|--help)
      # Print only the leading doc block (stop at the first non-comment line).
      awk 'NR==1{next} /^#/{sub(/^# ?/,""); print; next} {exit}' "$0"
      exit 0 ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

# ── Pretty logging ──────────────────────────────────────────────────────────
c_blue=$'\033[34m'; c_green=$'\033[32m'; c_yellow=$'\033[33m'; c_red=$'\033[31m'; c_off=$'\033[0m'
log()  { echo "${c_blue}[dev]${c_off} $*"; }
ok()   { echo "${c_green}[dev]${c_off} $*"; }
warn() { echo "${c_yellow}[dev]${c_off} $*"; }
err()  { echo "${c_red}[dev]${c_off} $*" >&2; }

PIDS=()
cleanup() {
  echo
  log "Shutting down…"
  for pid in "${PIDS[@]:-}"; do
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
      # Kill the whole process group so gradle/vite children die too.
      kill -- "-$pid" 2>/dev/null || kill "$pid" 2>/dev/null || true
    fi
  done
  wait 2>/dev/null || true
  ok "Stopped."
}
trap cleanup INT TERM EXIT

# ── Database ────────────────────────────────────────────────────────────────
db_ready() {
  # Returns 0 if something is listening on the DB port.
  if command -v nc >/dev/null 2>&1; then
    nc -z "$DB_HOST" "$DB_PORT" >/dev/null 2>&1
  else
    (exec 3<>"/dev/tcp/$DB_HOST/$DB_PORT") >/dev/null 2>&1
  fi
}

if [[ "$START_DB" == "1" ]]; then
  if db_ready; then
    ok "Postgres already reachable on $DB_HOST:$DB_PORT."
  elif command -v docker >/dev/null 2>&1; then
    log "Starting Postgres via docker compose…"
    docker compose -f "$ROOT_DIR/docker-compose.yml" up -d db
    log "Waiting for Postgres to accept connections…"
    for _ in $(seq 1 30); do db_ready && break; sleep 1; done
    db_ready && ok "Postgres is up." || { err "Postgres did not become ready in time."; exit 1; }
  else
    err "No database on $DB_HOST:$DB_PORT and Docker is not installed."
    err "Start Postgres yourself (or install Docker), then re-run with --no-db."
    exit 1
  fi
fi

# ── Backend ─────────────────────────────────────────────────────────────────
if [[ "$START_BACKEND" == "1" ]]; then
  # Auto-detect a JDK if one isn't already on PATH / JAVA_HOME (Homebrew keg-only).
  if [[ -z "${JAVA_HOME:-}" ]] && ! command -v java >/dev/null 2>&1; then
    if command -v brew >/dev/null 2>&1 && brew --prefix openjdk@21 >/dev/null 2>&1; then
      export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
      export PATH="$JAVA_HOME/bin:$PATH"
      log "Using JDK at $JAVA_HOME"
    fi
  fi
  if [[ -z "${JAVA_HOME:-}" ]] && ! command -v java >/dev/null 2>&1; then
    err "Java not found. The backend needs JDK 21 (e.g. 'brew install openjdk@21')."
    err "Set JAVA_HOME or add java to PATH, then re-run. To skip: ./dev.sh --frontend"
    exit 1
  fi
  log "Starting backend (Spring Boot, profile=$SPRING_PROFILE)…"
  ( cd "$BACKEND_DIR" && exec ./gradlew bootRun --args="--spring.profiles.active=$SPRING_PROFILE" ) &
  PIDS+=("$!")
  ok "Backend launching → http://localhost:8080/api (swagger: /api/swagger-ui.html)"
fi

# ── Frontend ────────────────────────────────────────────────────────────────
if [[ "$START_FRONTEND" == "1" ]]; then
  if [[ ! -d "$FRONTEND_DIR/node_modules" ]]; then
    log "Installing frontend dependencies…"
    ( cd "$FRONTEND_DIR" && npm install --legacy-peer-deps )
  fi
  log "Starting frontend (Vite)…"
  ( cd "$FRONTEND_DIR" && exec npm run dev ) &
  PIDS+=("$!")
  ok "Frontend launching → http://localhost:5173"
fi

log "Press Ctrl+C to stop all services."
wait
