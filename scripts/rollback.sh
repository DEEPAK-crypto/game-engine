#!/usr/bin/env bash
set -euo pipefail

# Rollback script for game-platform
# Usage: ./scripts/rollback.sh <environment> [revision]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Colors for output
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

usage() {
    echo "Usage: $0 <environment> [revision]"
    echo ""
    echo "Environments:"
    echo "  dev         - Development environment"
    echo "  staging     - Staging environment"
    echo "  production  - Production environment"
    echo ""
    echo "Options:"
    echo "  revision    - Specific revision number to rollback to (default: previous)"
    echo ""
    echo "Examples:"
    echo "  $0 staging           # Rollback to previous revision"
    echo "  $0 production 3      # Rollback to revision 3"
    exit 1
}

get_namespace() {
    local env="$1"
    case "$env" in
        dev)
            echo "game-platform-dev"
            ;;
        staging)
            echo "game-platform-staging"
            ;;
        production)
            echo "game-platform"
            ;;
        *)
            log_error "Invalid environment: $env"
            exit 1
            ;;
    esac
}

rollback() {
    local environment="$1"
    local revision="${2:-}"
    local namespace
    namespace=$(get_namespace "$environment")

    log_info "Rolling back in ${environment} environment (namespace: ${namespace})"

    # Show current rollout history
    log_info "Current rollout history:"
    kubectl -n "$namespace" rollout history deployment/game-service

    # Perform rollback
    if [[ -n "$revision" ]]; then
        log_info "Rolling back to revision ${revision}..."
        kubectl -n "$namespace" rollout undo deployment/game-service --to-revision="$revision"
    else
        log_info "Rolling back to previous revision..."
        kubectl -n "$namespace" rollout undo deployment/game-service
    fi

    # Wait for rollback
    log_info "Waiting for rollback to complete..."
    if kubectl -n "$namespace" rollout status deployment/game-service --timeout=300s; then
        log_info "Rollback successful!"
    else
        log_error "Rollback failed!"
        exit 1
    fi

    # Show new status
    log_info "Current deployment status:"
    kubectl -n "$namespace" get pods -l app=game-service
}

main() {
    if [[ $# -lt 1 ]]; then
        usage
    fi

    local environment="$1"
    local revision="${2:-}"

    if [[ "$environment" == "production" ]]; then
        log_warn "You are about to rollback in PRODUCTION!"
        read -p "Are you sure you want to continue? (yes/no): " confirm
        if [[ "$confirm" != "yes" ]]; then
            log_info "Rollback cancelled."
            exit 0
        fi
    fi

    rollback "$environment" "$revision"
}

main "$@"
