#!/usr/bin/env bash
#
# Local development helper.
#
#   ./start-dev.sh              start the whole stack in Docker
#   ./start-dev.sh cluster      same, but with three booking-service replicas
#   ./start-dev.sh infra        only Postgres, Redis and RabbitMQ (run services from your IDE)
#   ./start-dev.sh build        build the Java modules and the frontend
#   ./start-dev.sh test         run every test suite
#   ./start-dev.sh logs [svc]   tail logs
#   ./start-dev.sh stop-leader  stop replica 1 to force a leader election
#   ./start-dev.sh stop         stop everything
#   ./start-dev.sh reset        stop and delete the volumes

set -euo pipefail

# Compose files live under infra/docker/, so every invocation pins the project directory back to
# the repository root; otherwise relative build contexts and volume mounts resolve inside
# infra/docker/ and nothing is found.
readonly COMPOSE=(docker compose
  -f infra/docker/docker-compose.yml
  --project-directory .)
readonly COMPOSE_CLUSTER=(docker compose
  -f infra/docker/docker-compose.yml
  -f infra/docker/docker-compose.cluster.yml
  --project-directory .)

readonly GREEN='\033[0;32m' BLUE='\033[0;34m' YELLOW='\033[1;33m' NC='\033[0m'
info()    { echo -e "${BLUE}[info]${NC}  $*"; }
success() { echo -e "${GREEN}[ ok ]${NC}  $*"; }
warn()    { echo -e "${YELLOW}[warn]${NC}  $*"; }

ensure_env_file() {
  if [[ -f .env ]]; then
    return
  fi
  info "Creating .env"
  cat > .env <<'EOF'
# Local development settings. Everything here has a working default except the API keys.

JWT_SECRET=local-development-secret-change-me-32
LOG_LEVEL=INFO

# Natural language search and the support assistant need this. Without it those two endpoints
# return 502 and the rest of the platform works normally.
ANTHROPIC_API_KEY=
CLAUDE_MODEL=claude-opus-5

# Stripe test keys. Without them, creating a booking fails at the payment-intent step.
STRIPE_SECRET_KEY=
STRIPE_PUBLISHABLE_KEY=
STRIPE_WEBHOOK_SECRET=
STRIPE_CURRENCY=usd

# Email is rendered and recorded but not sent unless this is true and SMTP is configured.
EMAIL_ENABLED=false
EMAIL_FROM=noreply@skyflow.local
EMAIL_USER=
EMAIL_PASSWORD=
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
EOF
  warn "Edit .env to add ANTHROPIC_API_KEY and Stripe test keys if you want those features"
}

print_urls() {
  cat <<'EOF'

  Frontend      http://localhost:3001
  API gateway   http://localhost:8080
  Cluster state http://localhost:8082/api/v1/cluster/status
  Swagger UI    http://localhost:8081/swagger-ui.html   (flight-service)
  RabbitMQ      http://localhost:15672                  (guest / guest)
  Prometheus    http://localhost:9090
  Grafana       http://localhost:3000                   (admin / admin)

EOF
}

case "${1:-up}" in
  up)
    ensure_env_file
    info "Starting the full stack (first build takes a few minutes)"
    "${COMPOSE[@]}" up --build -d
    success "Stack is up"
    print_urls
    ;;

  cluster)
    ensure_env_file
    info "Starting with three booking-service replicas"
    "${COMPOSE_CLUSTER[@]}" up --build -d
    success "Stack is up"
    cat <<'EOF'

  Replica endpoints: localhost:8082, localhost:8092, localhost:8093

  Which one is master?
    curl -s localhost:8082/api/v1/cluster/status | jq '{nodeId,role,term,leaderId}'

  Kill it and watch a new term begin:
    docker compose stop booking-service
    curl -s localhost:8092/api/v1/cluster/status | jq '{nodeId,role,term,leaderId}'

EOF
    ;;

  infra)
    ensure_env_file
    info "Starting Postgres, Redis and RabbitMQ only"
    "${COMPOSE[@]}" up -d postgres redis rabbitmq
    success "Infrastructure is up; run the services from your IDE against localhost"
    ;;

  build)
    info "Building Java modules"
    ./mvnw -B clean install -DskipTests
    info "Building the frontend"
    (cd frontend && npm ci && npm run build)
    success "Build complete"
    ;;

  test)
    info "Running Java tests"
    ./mvnw -B test
    info "Running frontend tests"
    (cd frontend && npm ci --silent && npm run type-check && npm test)
    success "All tests passed"
    ;;

  stop-leader)
    # Kills the first booking replica so a new leader has to be elected. Which replica actually
    # holds the master role is whatever /api/v1/cluster/status reports.
    info "Stopping booking-service (replica 1) to force an election"
    "${COMPOSE_CLUSTER[@]}" stop booking-service
    success "Stopped. Check the survivors:"
    echo "    curl -s localhost:8092/api/v1/cluster/status | jq '{nodeId,role,term,leaderId}'"
    echo "    curl -s localhost:8093/api/v1/cluster/status | jq '{nodeId,role,term,leaderId}'"
    ;;

  logs)
    shift || true
    "${COMPOSE_CLUSTER[@]}" logs -f --tail=100 "$@"
    ;;

  stop)
    "${COMPOSE_CLUSTER[@]}" down
    success "Stopped"
    ;;

  reset)
    warn "This deletes the Postgres, Redis and RabbitMQ volumes"
    read -r -p "Continue? [y/N] " reply
    if [[ "$reply" == "y" || "$reply" == "Y" ]]; then
      "${COMPOSE_CLUSTER[@]}" down -v
      success "Reset complete"
    else
      info "Cancelled"
    fi
    ;;

  *)
    sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'
    ;;
esac
