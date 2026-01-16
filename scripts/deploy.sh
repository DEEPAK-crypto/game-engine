#!/usr/bin/env bash
set -euo pipefail

# Deployment script for game-platform
# Usage: ./scripts/deploy.sh <environment> [image-tag]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

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
    echo "Usage: $0 <environment> [image-tag]"
    echo ""
    echo "Environments:"
    echo "  dev         - Development environment"
    echo "  staging     - Staging environment"
    echo "  production  - Production environment"
    echo ""
    echo "Options:"
    echo "  image-tag   - Docker image tag to deploy (default: latest)"
    echo ""
    echo "Examples:"
    echo "  $0 dev"
    echo "  $0 staging v1.2.3"
    echo "  $0 production sha-abc1234"
    exit 1
}

# Check required tools
check_requirements() {
    local missing=()

    for cmd in kubectl kustomize; do
        if ! command -v "$cmd" &> /dev/null; then
            missing+=("$cmd")
        fi
    done

    if [[ ${#missing[@]} -gt 0 ]]; then
        log_error "Missing required tools: ${missing[*]}"
        exit 1
    fi
}

# Validate environment
validate_environment() {
    local env="$1"
    if [[ ! "$env" =~ ^(dev|staging|production)$ ]]; then
        log_error "Invalid environment: $env"
        usage
    fi
}

# Deploy to environment
deploy() {
    local environment="$1"
    local image_tag="${2:-latest}"
    local overlay_dir="${PROJECT_ROOT}/k8s/overlays/${environment}"

    log_info "Deploying to ${environment} with image tag: ${image_tag}"

    # Check if overlay exists
    if [[ ! -d "$overlay_dir" ]]; then
        log_error "Overlay directory not found: $overlay_dir"
        exit 1
    fi

    # Update image tag
    cd "$overlay_dir"
    kustomize edit set image "game-service=ghcr.io/your-org/game-engine/game-service:${image_tag}"

    # Apply configuration
    log_info "Applying Kubernetes configuration..."
    kustomize build . | kubectl apply -f -

    # Get namespace based on environment
    local namespace
    case "$environment" in
        dev)
            namespace="game-platform-dev"
            ;;
        staging)
            namespace="game-platform-staging"
            ;;
        production)
            namespace="game-platform"
            ;;
    esac

    # Wait for rollout
    log_info "Waiting for rollout to complete..."
    if kubectl -n "$namespace" rollout status deployment/game-service --timeout=300s; then
        log_info "Deployment successful!"
    else
        log_error "Deployment failed!"
        kubectl -n "$namespace" rollout undo deployment/game-service
        exit 1
    fi

    # Show deployment status
    log_info "Deployment status:"
    kubectl -n "$namespace" get pods -l app=game-service
}

# Main
main() {
    if [[ $# -lt 1 ]]; then
        usage
    fi

    local environment="$1"
    local image_tag="${2:-latest}"

    check_requirements
    validate_environment "$environment"

    if [[ "$environment" == "production" ]]; then
        log_warn "You are about to deploy to PRODUCTION!"
        read -p "Are you sure you want to continue? (yes/no): " confirm
        if [[ "$confirm" != "yes" ]]; then
            log_info "Deployment cancelled."
            exit 0
        fi
    fi

    deploy "$environment" "$image_tag"
}

main "$@"
