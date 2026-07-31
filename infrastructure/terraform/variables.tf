variable "aws_region" {
  type        = string
  description = "AWS region for the quality engineering sandbox"
  default     = "eu-west-2"
}

variable "environment" {
  type        = string
  description = "Environment name"
  default     = "test"
}
