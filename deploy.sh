#!/usr/bin/env bash
#
# Build, push and deploy SkyFlow.
#
#   ./deploy.sh build            build every image locally
#   ./deploy.sh push             build and push to ECR
#   ./deploy.sh k8s              render manifests and apply them
#   ./deploy.sh deploy           push + k8s + wait for rollout
#   ./deploy.sh status           show what is running
#   ./deploy.sh cluster          show which booking replica is master
#   ./deploy.sh rollback <svc>   undo the last rollout of one service
#
# Required for push/deploy: AWS_REGION, ECR_REGISTRY.
# Required for k8s/deploy:  the secret values listed in require_secrets below.

set -euo pipefail

readonly NAMESPACE="${NAMESPACE:-skyflow}"
readonly IMAGE_TAG="${IMAGE_TAG:-$(git rev-parse --short HEAD 2>/dev/null || echo latest)}"
readonly AWS_REGION="${AWS_REGION:-us-east-1}"

# Java service modules, and therefore image names (skyflow-<module>).
readonly JAVA_MODULES=(
  api-gateway
  user-service
  flight-service
  booking-service
  payment-service
  notification-service
  ai-service
)

readonly MANIFESTS=(
  namespace.yaml
  configmap.yaml
  secrets.yaml
  api-gateway.yaml
  user-service.yaml
  flight-service.yaml
  booking-service.yaml
  payment-service.yaml
  notification-service.yaml
  ai-service.yaml
  frontend.yaml
  networkpolicy.yaml
  ingress.yaml
)

# ------------------------------------------------------------------ output

readonly RED='\033[0;31m' GREEN='\033[0;32m' YELLOW='\033[1;33m' BLUE='\033[0;34m' NC='\033[0m'

info()    { echo -e "${BLUE}[info]${NC}  $*"; }
success() { echo -e "${GREEN}[ ok ]${NC}  $*"; }
warn()    { echo -e "${YELLOW}[warn]${NC}  $*"; }
fail()    { echo -e "${RED}[fail]${NC}  $*" >&2; exit 1; }

require_tool() {
  command -v "$1" >/dev/null 2>&1 || fail "$1 is required but not installed"
}

require_env() {
  for name in "$@"; do
    [[ -n "${!name:-}" ]] || fail "$name must be set"
  done
}

# ------------------------------------------------------------------ build

run_tests() {
  info "Running the Java test suite"
  ./mvnw -B -q test
  success "Java tests passed"

  info "Running the frontend checks"
  (cd frontend && npm ci --silent && npm run type-check && npm test)
  success "Frontend checks passed"
}

build_images() {
  require_tool docker
  local registry_prefix="${ECR_REGISTRY:+${ECR_REGISTRY}/}"

  for module in "${JAVA_MODULES[@]}"; do
    info "Building skyflow-${module}:${IMAGE_TAG}"
    docker build \
      --build-arg "MODULE=${module}" \
      -t "${registry_prefix}skyflow-${module}:${IMAGE_TAG}" \
      -t "${registry_prefix}skyflow-${module}:latest" \
      .
  done

  info "Building skyflow-frontend:${IMAGE_TAG}"
  docker build \
    -t "${registry_prefix}skyflow-frontend:${IMAGE_TAG}" \
    -t "${registry_prefix}skyflow-frontend:latest" \
    ./frontend

  success "All images built"
}

push_images() {
  require_tool aws
  require_env ECR_REGISTRY

  info "Signing in to ECR"
  aws ecr get-login-password --region "$AWS_REGION" \
    | docker login --username AWS --password-stdin "$ECR_REGISTRY"

  for module in "${JAVA_MODULES[@]}" frontend; do
    # Repositories are created by Terraform; this keeps a first deploy from failing on a
    # missing repo when Terraform has not been applied yet.
    aws ecr describe-repositories --repository-names "skyflow-${module}" \
      --region "$AWS_REGION" >/dev/null 2>&1 \
      || aws ecr create-repository --repository-name "skyflow-${module}" \
           --region "$AWS_REGION" --image-scanning-configuration scanOnPush=true >/dev/null

    info "Pushing skyflow-${module}:${IMAGE_TAG}"
    docker push "${ECR_REGISTRY}/skyflow-${module}:${IMAGE_TAG}"
    docker push "${ECR_REGISTRY}/skyflow-${module}:latest"
  done

  success "All images pushed"
}

# ------------------------------------------------------------------ kubernetes

require_secrets() {
  require_env \
    DB_URL DB_USER DB_PASSWORD \
    REDIS_HOST RABBITMQ_HOST RABBITMQ_USER RABBITMQ_PASSWORD \
    JWT_SECRET

  if (( ${#JWT_SECRET} < 32 )); then
    fail "JWT_SECRET must be at least 32 characters (HS256 signing key)"
  fi
}

# Base64 for the Secret manifest. Newline-free, which matters: a trailing \n in a password is a
# very confusing authentication failure.
b64() {
  printf '%s' "${1:-}" | base64 | tr -d '\n'
}

render_manifests() {
  local out_dir=$1
  mkdir -p "$out_dir"

  export DB_URL_B64 DB_USER_B64 DB_PASSWORD_B64 REDIS_HOST_B64 REDIS_PASSWORD_B64
  export RABBITMQ_HOST_B64 RABBITMQ_USER_B64 RABBITMQ_PASSWORD_B64 JWT_SECRET_B64
  export STRIPE_SECRET_KEY_B64 STRIPE_PUBLISHABLE_KEY_B64 STRIPE_WEBHOOK_SECRET_B64
  export ANTHROPIC_API_KEY_B64 EMAIL_USER_B64 EMAIL_PASSWORD_B64 ECR_DOCKER_CONFIG_B64

  DB_URL_B64=$(b64 "$DB_URL")
  DB_USER_B64=$(b64 "$DB_USER")
  DB_PASSWORD_B64=$(b64 "$DB_PASSWORD")
  REDIS_HOST_B64=$(b64 "$REDIS_HOST")
  REDIS_PASSWORD_B64=$(b64 "${REDIS_PASSWORD:-}")
  RABBITMQ_HOST_B64=$(b64 "$RABBITMQ_HOST")
  RABBITMQ_USER_B64=$(b64 "$RABBITMQ_USER")
  RABBITMQ_PASSWORD_B64=$(b64 "$RABBITMQ_PASSWORD")
  JWT_SECRET_B64=$(b64 "$JWT_SECRET")
  STRIPE_SECRET_KEY_B64=$(b64 "${STRIPE_SECRET_KEY:-}")
  STRIPE_PUBLISHABLE_KEY_B64=$(b64 "${STRIPE_PUBLISHABLE_KEY:-}")
  STRIPE_WEBHOOK_SECRET_B64=$(b64 "${STRIPE_WEBHOOK_SECRET:-}")
  ANTHROPIC_API_KEY_B64=$(b64 "${ANTHROPIC_API_KEY:-}")
  EMAIL_USER_B64=$(b64 "${EMAIL_USER:-}")
  EMAIL_PASSWORD_B64=$(b64 "${EMAIL_PASSWORD:-}")
  ECR_DOCKER_CONFIG_B64=$(b64 "${ECR_DOCKER_CONFIG:-{}}")

  export ECR_REGISTRY IMAGE_TAG ACM_CERTIFICATE_ARN="${ACM_CERTIFICATE_ARN:-}"

  for manifest in "${MANIFESTS[@]}"; do
    envsubst < "infra/k8s/${manifest}" > "${out_dir}/${manifest}"
  done

  [[ -n "${STRIPE_WEBHOOK_SECRET:-}" ]] \
    || warn "STRIPE_WEBHOOK_SECRET is empty: the webhook endpoint will reject every request"
  [[ -n "${ANTHROPIC_API_KEY:-}" ]] \
    || warn "ANTHROPIC_API_KEY is empty: the AI endpoints will report 502"
}

apply_manifests() {
  require_tool kubectl
  require_tool envsubst
  require_env ECR_REGISTRY
  require_secrets

  local out_dir
  out_dir=$(mktemp -d)
  # Rendered manifests contain secrets, so they never outlive the run.
  trap 'rm -rf "$out_dir"' RETURN

  render_manifests "$out_dir"

  info "Applying manifests to namespace ${NAMESPACE}"
  kubectl apply -f "${out_dir}/namespace.yaml"
  for manifest in "${MANIFESTS[@]:1}"; do
    kubectl apply -f "${out_dir}/${manifest}"
  done
  success "Manifests applied"
}

wait_for_rollout() {
  require_tool kubectl
  for module in "${JAVA_MODULES[@]}"; do
    if [[ "$module" == "booking-service" ]]; then
      info "Waiting for statefulset/booking-service"
      kubectl rollout status "statefulset/${module}" -n "$NAMESPACE" --timeout=5m
    else
      info "Waiting for deployment/${module}"
      kubectl rollout status "deployment/${module}" -n "$NAMESPACE" --timeout=5m
    fi
  done
  kubectl rollout status deployment/frontend -n "$NAMESPACE" --timeout=3m
  success "All workloads rolled out"
}

show_status() {
  require_tool kubectl
  echo
  kubectl get pods -n "$NAMESPACE" -o wide
  echo
  kubectl get svc,ingress,hpa -n "$NAMESPACE"
}

show_cluster() {
  require_tool kubectl
  info "Coordination state of each booking-service replica"
  for pod in $(kubectl get pods -n "$NAMESPACE" -l app=booking-service \
      -o jsonpath='{.items[*].metadata.name}'); do
    echo "--- $pod"
    kubectl exec -n "$NAMESPACE" "$pod" -- \
      sh -c 'curl -fsS localhost:8082/api/v1/cluster/status' 2>/dev/null \
      || warn "  could not reach $pod"
    echo
  done
}

rollback() {
  require_tool kubectl
  local target=${1:-}
  [[ -n "$target" ]] || fail "usage: ./deploy.sh rollback <service>"

  if [[ "$target" == "booking-service" ]]; then
    kubectl rollout undo "statefulset/${target}" -n "$NAMESPACE"
  else
    kubectl rollout undo "deployment/${target}" -n "$NAMESPACE"
  fi
  success "Rolled back ${target}"
}

usage() {
  sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
}

main() {
  case "${1:-help}" in
    test)     run_tests ;;
    build)    build_images ;;
    push)     build_images && push_images ;;
    k8s)      apply_manifests ;;
    deploy)   build_images && push_images && apply_manifests && wait_for_rollout && show_status ;;
    status)   show_status ;;
    cluster)  show_cluster ;;
    rollback) rollback "${2:-}" ;;
    help|-h|--help) usage ;;
    *)        fail "Unknown command: $1 (try ./deploy.sh help)" ;;
  esac
}

main "$@"
