#!/bin/bash

# SkyFlow Deployment Script
# This script automates the deployment of the SkyFlow application

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PROJECT_NAME="skyflow"
AWS_REGION="us-west-2"
ENVIRONMENT="production"
DOMAIN_NAME="skyflow.example.com"

# Functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

check_prerequisites() {
    log_info "Checking prerequisites..."
    
    # Check if required tools are installed
    command -v terraform >/dev/null 2>&1 || { log_error "Terraform is required but not installed. Aborting."; exit 1; }
    command -v aws >/dev/null 2>&1 || { log_error "AWS CLI is required but not installed. Aborting."; exit 1; }
    command -v kubectl >/dev/null 2>&1 || { log_error "kubectl is required but not installed. Aborting."; exit 1; }
    command -v docker >/dev/null 2>&1 || { log_error "Docker is required but not installed. Aborting."; exit 1; }
    command -v node >/dev/null 2>&1 || { log_error "Node.js is required but not installed. Aborting."; exit 1; }
    
    # Check AWS credentials
    if ! aws sts get-caller-identity >/dev/null 2>&1; then
        log_error "AWS credentials not configured. Please run 'aws configure' first."
        exit 1
    fi
    
    log_success "Prerequisites check passed"
}

setup_terraform() {
    log_info "Setting up Terraform..."
    
    cd terraform
    
    # Initialize Terraform
    terraform init
    
    # Check if terraform.tfvars exists
    if [ ! -f "terraform.tfvars" ]; then
        log_warning "terraform.tfvars not found. Creating from example..."
        cp terraform.tfvars.example terraform.tfvars
        log_warning "Please edit terraform.tfvars with your configuration before continuing."
        read -p "Press Enter to continue after editing terraform.tfvars..."
    fi
    
    # Plan Terraform
    log_info "Planning Terraform deployment..."
    terraform plan -out=tfplan
    
    # Ask for confirmation
    read -p "Do you want to apply the Terraform plan? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        log_info "Applying Terraform plan..."
        terraform apply tfplan
        log_success "Terraform deployment completed"
    else
        log_warning "Terraform deployment cancelled"
        exit 1
    fi
    
    cd ..
}

build_and_push_images() {
    log_info "Building and pushing Docker images..."
    
    # Get ECR registry from Terraform output
    ECR_REGISTRY=$(cd terraform && terraform output -raw ecr_registry)
    
    # Build and push backend image
    log_info "Building backend image..."
    docker build -t $ECR_REGISTRY/skyflow-backend:latest .
    docker push $ECR_REGISTRY/skyflow-backend:latest
    
    # Build and push frontend image
    log_info "Building frontend image..."
    cd frontend
    npm install
    npm run build
    docker build -t $ECR_REGISTRY/skyflow-frontend:latest .
    docker push $ECR_REGISTRY/skyflow-frontend:latest
    cd ..
    
    log_success "Docker images built and pushed successfully"
}

setup_kubernetes() {
    log_info "Setting up Kubernetes resources..."
    
    # Get cluster name from Terraform output
    CLUSTER_NAME=$(cd terraform && terraform output -raw cluster_name)
    
    # Update kubeconfig
    aws eks update-kubeconfig --region $AWS_REGION --name $CLUSTER_NAME
    
    # Create namespace and resources
    kubectl apply -f k8s/namespace.yaml
    
    # Create secrets (you'll need to populate these)
    log_warning "Please create the required secrets before continuing:"
    log_warning "1. Update k8s/secrets.yaml with base64 encoded values"
    log_warning "2. Apply the secrets: kubectl apply -f k8s/secrets.yaml"
    read -p "Press Enter after creating secrets..."
    
    # Deploy applications
    kubectl apply -f k8s/backend-deployment.yaml
    kubectl apply -f k8s/frontend-deployment.yaml
    kubectl apply -f k8s/ingress.yaml
    
    log_success "Kubernetes resources deployed successfully"
}

wait_for_deployment() {
    log_info "Waiting for deployment to be ready..."
    
    # Wait for backend deployment
    kubectl wait --for=condition=available --timeout=300s deployment/skyflow-backend -n skyflow
    
    # Wait for frontend deployment
    kubectl wait --for=condition=available --timeout=300s deployment/skyflow-frontend -n skyflow
    
    log_success "All deployments are ready"
}

run_tests() {
    log_info "Running tests..."
    
    # Run backend tests
    cd backend
    npm test
    cd ..
    
    # Run frontend tests
    cd frontend
    npm test
    cd ..
    
    log_success "All tests passed"
}

show_deployment_info() {
    log_info "Deployment completed successfully!"
    
    # Get outputs from Terraform
    cd terraform
    FRONTEND_URL=$(terraform output -raw frontend_url)
    API_URL=$(terraform output -raw api_url)
    CLUSTER_NAME=$(terraform output -raw cluster_name)
    cd ..
    
    echo
    echo "=== Deployment Information ==="
    echo "Frontend URL: $FRONTEND_URL"
    echo "API URL: $API_URL"
    echo "EKS Cluster: $CLUSTER_NAME"
    echo "Region: $AWS_REGION"
    echo
    echo "=== Useful Commands ==="
    echo "View pods: kubectl get pods -n skyflow"
    echo "View services: kubectl get svc -n skyflow"
    echo "View ingress: kubectl get ingress -n skyflow"
    echo "View logs: kubectl logs -f deployment/skyflow-backend -n skyflow"
    echo "Access cluster: aws eks update-kubeconfig --region $AWS_REGION --name $CLUSTER_NAME"
    echo
}

cleanup() {
    log_warning "Cleaning up..."
    
    # Remove Terraform plan file
    rm -f terraform/tfplan
    
    log_success "Cleanup completed"
}

# Main deployment function
deploy() {
    log_info "Starting SkyFlow deployment..."
    
    check_prerequisites
    setup_terraform
    build_and_push_images
    setup_kubernetes
    wait_for_deployment
    run_tests
    show_deployment_info
    cleanup
    
    log_success "SkyFlow deployment completed successfully!"
}

# Destroy function
destroy() {
    log_warning "Destroying SkyFlow infrastructure..."
    
    read -p "Are you sure you want to destroy all resources? This action cannot be undone. (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        cd terraform
        terraform destroy -auto-approve
        cd ..
        log_success "Infrastructure destroyed successfully"
    else
        log_info "Destroy cancelled"
    fi
}

# Help function
show_help() {
    echo "SkyFlow Deployment Script"
    echo
    echo "Usage: $0 [COMMAND]"
    echo
    echo "Commands:"
    echo "  deploy    Deploy the complete SkyFlow application"
    echo "  destroy   Destroy all infrastructure (use with caution)"
    echo "  help      Show this help message"
    echo
    echo "Examples:"
    echo "  $0 deploy"
    echo "  $0 destroy"
    echo
}

# Main script logic
case "${1:-deploy}" in
    deploy)
        deploy
        ;;
    destroy)
        destroy
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        log_error "Unknown command: $1"
        show_help
        exit 1
        ;;
esac
