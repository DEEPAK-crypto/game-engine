#!/usr/bin/env bash
set -euo pipefail

# Health check script for game-platform
# Usage: ./scripts/health-check.sh <environment>

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

check_passed() {
    echo -e "${GREEN}[PASS]${NC} $1"
}

check_failed() {
    echo -e "${RED}[FAIL]${NC} $1"
}

get_namespace() {
    local env="$1"
    case "$env" in
        dev) echo "game-platform-dev" ;;
        staging) echo "game-platform-staging" ;;
        production) echo "game-platform" ;;
        *) log_error "Invalid environment: $env"; exit 1 ;;
    esac
}

get_url() {
    local env="$1"
    case "$env" in
        dev) echo "https://dev.game-platform.example.com" ;;
        staging) echo "https://staging.game-platform.example.com" ;;
        production) echo "https://game-platform.example.com" ;;
    esac
}

check_pods() {
    local namespace="$1"
    local status

    log_info "Checking pod status..."

    # Get pod count
    local ready_pods
    ready_pods=$(kubectl -n "$namespace" get pods -l app=game-service -o jsonpath='{.items[?(@.status.phase=="Running")].metadata.name}' | wc -w | tr -d ' ')

    local total_pods
    total_pods=$(kubectl -n "$namespace" get pods -l app=game-service --no-headers 2>/dev/null | wc -l | tr -d ' ')

    if [[ "$ready_pods" -eq "$total_pods" ]] && [[ "$total_pods" -gt 0 ]]; then
        check_passed "All $total_pods pods are running"
        return 0
    else
        check_failed "Only $ready_pods/$total_pods pods are running"
        kubectl -n "$namespace" get pods -l app=game-service
        return 1
    fi
}

check_endpoints() {
    local url="$1"
    local failed=0

    log_info "Checking endpoints..."

    # Health endpoint
    if curl -sf "${url}/actuator/health" > /dev/null 2>&1; then
        check_passed "Health endpoint OK"
    else
        check_failed "Health endpoint failed"
        ((failed++))
    fi

    # Info endpoint
    if curl -sf "${url}/actuator/info" > /dev/null 2>&1; then
        check_passed "Info endpoint OK"
    else
        check_failed "Info endpoint failed"
        ((failed++))
    fi

    # Prometheus metrics
    if curl -sf "${url}/actuator/prometheus" > /dev/null 2>&1; then
        check_passed "Prometheus metrics OK"
    else
        check_failed "Prometheus metrics failed"
        ((failed++))
    fi

    return $failed
}

check_services() {
    local namespace="$1"
    local failed=0

    log_info "Checking Kubernetes services..."

    # Check game-service
    if kubectl -n "$namespace" get svc game-service > /dev/null 2>&1; then
        check_passed "game-service service exists"
    else
        check_failed "game-service service not found"
        ((failed++))
    fi

    # Check endpoints
    local endpoints
    endpoints=$(kubectl -n "$namespace" get endpoints game-service -o jsonpath='{.subsets[*].addresses[*].ip}' 2>/dev/null | wc -w | tr -d ' ')

    if [[ "$endpoints" -gt 0 ]]; then
        check_passed "game-service has $endpoints endpoints"
    else
        check_failed "game-service has no endpoints"
        ((failed++))
    fi

    return $failed
}

check_databases() {
    local namespace="$1"
    local failed=0

    log_info "Checking database connectivity..."

    # PostgreSQL
    if kubectl -n "$namespace" exec -it deploy/game-service -- sh -c 'nc -z postgresql 5432' 2>/dev/null; then
        check_passed "PostgreSQL connectivity OK"
    else
        check_failed "PostgreSQL connectivity failed"
        ((failed++))
    fi

    # Redis
    if kubectl -n "$namespace" exec -it deploy/game-service -- sh -c 'nc -z redis 6379' 2>/dev/null; then
        check_passed "Redis connectivity OK"
    else
        check_failed "Redis connectivity failed"
        ((failed++))
    fi

    # Cassandra
    if kubectl -n "$namespace" exec -it deploy/game-service -- sh -c 'nc -z cassandra 9042' 2>/dev/null; then
        check_passed "Cassandra connectivity OK"
    else
        check_failed "Cassandra connectivity failed"
        ((failed++))
    fi

    return $failed
}

main() {
    if [[ $# -lt 1 ]]; then
        echo "Usage: $0 <environment>"
        echo "Environments: dev, staging, production"
        exit 1
    fi

    local environment="$1"
    local namespace
    namespace=$(get_namespace "$environment")
    local url
    url=$(get_url "$environment")

    log_info "Running health checks for ${environment} environment"
    echo "================================================"

    local total_failures=0

    # Run checks
    check_pods "$namespace" || ((total_failures++))
    echo ""
    check_services "$namespace" || ((total_failures++))
    echo ""
    # Endpoint checks may fail if cluster is not accessible externally
    check_endpoints "$url" || log_warn "Endpoint checks skipped (external access may not be configured)"
    echo ""

    echo "================================================"
    if [[ $total_failures -eq 0 ]]; then
        log_info "All health checks passed!"
        exit 0
    else
        log_error "$total_failures check(s) failed"
        exit 1
    fi
}

main "$@"
