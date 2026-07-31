provider "aws" {
  region = var.aws_region
}

data "aws_caller_identity" "current" {}

resource "aws_s3_bucket" "order_audit" {
  bucket = "qe-order-audit-${var.environment}-${data.aws_caller_identity.current.account_id}"
  tags = {
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_kms_key" "order_audit" {
  description             = "Encryption key for order audit records"
  deletion_window_in_days = 7
  enable_key_rotation     = true

  tags = {
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_kms_alias" "order_audit" {
  name          = "alias/qe-order-audit-${var.environment}"
  target_key_id = aws_kms_key.order_audit.key_id
}

resource "aws_s3_bucket_public_access_block" "order_audit" {
  bucket                  = aws_s3_bucket.order_audit.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "order_audit" {
  bucket = aws_s3_bucket.order_audit.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "order_audit" {
  bucket = aws_s3_bucket.order_audit.id

  rule {
    apply_server_side_encryption_by_default {
      kms_master_key_id = aws_kms_key.order_audit.arn
      sse_algorithm     = "aws:kms"
    }
    bucket_key_enabled = true
  }
}

resource "aws_iam_role" "order_audit_lambda" {
  name = "qe-order-audit-${var.environment}"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "order_audit_write" {
  role = aws_iam_role.order_audit_lambda.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["s3:PutObject"]
        Resource = "${aws_s3_bucket.order_audit.arn}/orders/*"
      },
      {
        Effect   = "Allow"
        Action   = ["kms:GenerateDataKey"]
        Resource = aws_kms_key.order_audit.arn
      }
    ]
  })
}
