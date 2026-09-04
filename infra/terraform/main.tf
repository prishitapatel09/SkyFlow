terraform {
  required_version = ">= 1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.0"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.0"
    }
  }

  backend "s3" {
    bucket = "skyflow-terraform-state"
    key    = "infrastructure/terraform.tfstate"
    region = "us-west-2"
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "SkyFlow"
      Environment = var.environment
      ManagedBy   = "Terraform"
    }
  }
}

# VPC and Networking
module "vpc" {
  source = "./modules/vpc"

  environment     = var.environment
  vpc_cidr        = var.vpc_cidr
  azs             = var.availability_zones
  public_subnets  = var.public_subnets
  private_subnets = var.private_subnets
}

# EKS Cluster
module "eks" {
  source = "./modules/eks"

  environment     = var.environment
  cluster_name    = var.cluster_name
  cluster_version = var.cluster_version
  vpc_id          = module.vpc.vpc_id
  private_subnets = module.vpc.private_subnets
  public_subnets  = module.vpc.public_subnets

  node_groups = {
    general = {
      desired_capacity = 2
      max_capacity     = 4
      min_capacity     = 1
      instance_types   = ["t3.medium"]
    }
    monitoring = {
      desired_capacity = 1
      max_capacity     = 2
      min_capacity     = 1
      instance_types   = ["t3.small"]
    }
  }
}

# RDS PostgreSQL
module "rds" {
  source = "./modules/rds"

  environment       = var.environment
  vpc_id            = module.vpc.vpc_id
  private_subnets   = module.vpc.private_subnets
  db_name           = var.db_name
  db_username       = var.db_username
  db_password       = var.db_password
  db_instance_class = var.db_instance_class
}

# ElastiCache Redis
module "redis" {
  source = "./modules/redis"

  environment     = var.environment
  vpc_id          = module.vpc.vpc_id
  private_subnets = module.vpc.private_subnets
  node_type       = var.redis_node_type
  num_cache_nodes = var.redis_num_cache_nodes
}

# Amazon MQ (RabbitMQ)
module "mq" {
  source = "./modules/mq"

  environment     = var.environment
  vpc_id          = module.vpc.vpc_id
  private_subnets = module.vpc.private_subnets
  instance_type   = var.mq_instance_type
}

# Application Load Balancer
module "alb" {
  source = "./modules/alb"

  environment    = var.environment
  vpc_id         = module.vpc.vpc_id
  public_subnets = module.vpc.public_subnets
  domain_name    = var.domain_name
}

# ECR Repositories
module "ecr" {
  source = "./modules/ecr"

  environment = var.environment
  repositories = [
    "skyflow-api-gateway",
    "skyflow-user-service",
    "skyflow-flight-service",
    "skyflow-booking-service",
    "skyflow-payment-service",
    "skyflow-notification-service",
    "skyflow-ai-service",
    "skyflow-frontend"
  ]
}

# CloudWatch Logs
module "cloudwatch" {
  source = "./modules/cloudwatch"

  environment = var.environment
  log_groups = [
    "/aws/eks/${var.cluster_name}/application",
    "/aws/eks/${var.cluster_name}/system",
    "/aws/rds/instance/${var.environment}-skyflow-postgres",
    "/aws/elasticache/${var.environment}-skyflow-redis"
  ]
}

# IAM Roles and Policies
module "iam" {
  source = "./modules/iam"

  environment  = var.environment
  cluster_name = var.cluster_name
  account_id   = data.aws_caller_identity.current.account_id
}

# Route53 DNS
module "route53" {
  source = "./modules/route53"

  environment  = var.environment
  domain_name  = var.domain_name
  alb_dns_name = module.alb.alb_dns_name
  alb_zone_id  = module.alb.alb_zone_id
}

# ACM Certificate
module "acm" {
  source = "./modules/acm"

  domain_name = var.domain_name
  environment = var.environment
}

# S3 Buckets
module "s3" {
  source = "./modules/s3"

  environment = var.environment
  domain_name = var.domain_name
  buckets = [
    "skyflow-frontend-assets",
    "skyflow-backup",
    "skyflow-logs"
  ]
}

# CloudFront Distribution
module "cloudfront" {
  source = "./modules/cloudfront"

  environment         = var.environment
  domain_name         = var.domain_name
  s3_bucket_name      = module.s3.frontend_bucket_name
  acm_certificate_arn = module.acm.certificate_arn
}

# Kubernetes Provider
provider "kubernetes" {
  host                   = module.eks.cluster_endpoint
  cluster_ca_certificate = base64decode(module.eks.cluster_certificate_authority_data)
  token                  = data.aws_eks_cluster_auth.cluster.token
}

provider "helm" {
  kubernetes {
    host                   = module.eks.cluster_endpoint
    cluster_ca_certificate = base64decode(module.eks.cluster_certificate_authority_data)
    token                  = data.aws_eks_cluster_auth.cluster.token
  }
}

# Data sources
data "aws_caller_identity" "current" {}
data "aws_eks_cluster_auth" "cluster" {
  name = module.eks.cluster_name
}

# Outputs
output "cluster_endpoint" {
  description = "Endpoint for EKS control plane"
  value       = module.eks.cluster_endpoint
}

output "cluster_name" {
  description = "Name of the EKS cluster"
  value       = module.eks.cluster_name
}

output "alb_dns_name" {
  description = "DNS name of the load balancer"
  value       = module.alb.alb_dns_name
}

output "rds_endpoint" {
  description = "RDS instance endpoint"
  value       = module.rds.endpoint
}

output "redis_endpoint" {
  description = "Redis cluster endpoint"
  value       = module.redis.endpoint
}

output "mq_endpoint" {
  description = "Amazon MQ endpoint"
  value       = module.mq.endpoint
}

output "frontend_url" {
  description = "Frontend application URL"
  value       = "https://${var.domain_name}"
}

output "api_url" {
  description = "Backend API URL"
  value       = "https://api.${var.domain_name}"
}
