variable "aws_access_key" {
  description = "Your AWS Access Key"
  type        = string
  sensitive   = true
}

variable "aws_secret_key" {
  description = "Your AWS Secret Key"
  type        = string
  sensitive   = true
}

variable "aws_region" {
  description = "AWS region to deploy to"
  type        = string
  default     = "ca-central-1"
}
