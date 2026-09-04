variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-west-2"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "production"

  validation {
    condition     = contains(["development", "staging", "production"], var.environment)
    error_message = "Environment must be one of: development, staging, production."
  }
}

variable "vpc_cidr" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "Availability zones"
  type        = list(string)
  default     = ["us-west-2a", "us-west-2b", "us-west-2c"]
}

variable "public_subnets" {
  description = "CIDR blocks for public subnets"
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
}

variable "private_subnets" {
  description = "CIDR blocks for private subnets"
  type        = list(string)
  default     = ["10.0.11.0/24", "10.0.12.0/24", "10.0.13.0/24"]
}

variable "cluster_name" {
  description = "Name of the EKS cluster"
  type        = string
  default     = "skyflow-cluster"
}

variable "cluster_version" {
  description = "Kubernetes version for EKS cluster"
  type        = string
  default     = "1.28"
}

variable "db_name" {
  description = "Name of the database"
  type        = string
  default     = "skyflow"
}

variable "db_username" {
  description = "Database master username"
  type        = string
  default     = "skyflow_admin"
}

variable "db_password" {
  description = "Database master password"
  type        = string
  sensitive   = true
}

variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t3.micro"
}

variable "redis_node_type" {
  description = "ElastiCache node type"
  type        = string
  default     = "cache.t3.micro"
}

variable "redis_num_cache_nodes" {
  description = "Number of cache nodes"
  type        = number
  default     = 1
}

variable "mq_instance_type" {
  description = "Amazon MQ instance type"
  type        = string
  default     = "mq.t3.micro"
}

variable "domain_name" {
  description = "Domain name for the application"
  type        = string
  default     = "skyflow.example.com"
}

variable "stripe_secret_key" {
  description = "Stripe secret key"
  type        = string
  sensitive   = true
}

variable "stripe_publishable_key" {
  description = "Stripe publishable key"
  type        = string
}

variable "stripe_webhook_secret" {
  description = "Stripe webhook secret"
  type        = string
  sensitive   = true
}

variable "anthropic_api_key" {
  description = "Anthropic API key used by ai-service for natural language search and support chat"
  type        = string
  sensitive   = true
  default     = ""
}

variable "jwt_secret" {
  description = "JWT secret key"
  type        = string
  sensitive   = true
}

variable "email_user" {
  description = "Email service username"
  type        = string
  default     = ""
}

variable "email_password" {
  description = "Email service password"
  type        = string
  sensitive   = true
  default     = ""
}

variable "redis_password" {
  description = "Redis password"
  type        = string
  sensitive   = true
}

variable "rabbitmq_username" {
  description = "RabbitMQ username"
  type        = string
  default     = "skyflow_user"
}

variable "rabbitmq_password" {
  description = "RabbitMQ password"
  type        = string
  sensitive   = true
}

variable "enable_monitoring" {
  description = "Enable monitoring stack (Prometheus/Grafana)"
  type        = bool
  default     = true
}

variable "enable_backup" {
  description = "Enable automated backups"
  type        = bool
  default     = true
}

variable "backup_retention_days" {
  description = "Number of days to retain backups"
  type        = number
  default     = 7
}

variable "enable_ssl" {
  description = "Enable SSL/TLS"
  type        = bool
  default     = true
}

variable "enable_waf" {
  description = "Enable AWS WAF"
  type        = bool
  default     = true
}

variable "enable_cloudfront" {
  description = "Enable CloudFront distribution"
  type        = bool
  default     = true
}

variable "enable_auto_scaling" {
  description = "Enable auto scaling"
  type        = bool
  default     = true
}

variable "min_capacity" {
  description = "Minimum capacity for auto scaling"
  type        = number
  default     = 1
}

variable "max_capacity" {
  description = "Maximum capacity for auto scaling"
  type        = number
  default     = 10
}

variable "desired_capacity" {
  description = "Desired capacity for auto scaling"
  type        = number
  default     = 2
}

variable "enable_logging" {
  description = "Enable comprehensive logging"
  type        = bool
  default     = true
}

variable "log_retention_days" {
  description = "Number of days to retain logs"
  type        = number
  default     = 30
}

variable "enable_alerts" {
  description = "Enable CloudWatch alarms"
  type        = bool
  default     = true
}

variable "notification_email" {
  description = "Email for notifications"
  type        = string
  default     = "admin@skyflow.example.com"
}

variable "tags" {
  description = "Additional tags for resources"
  type        = map(string)
  default     = {}
}
