output "audit_bucket_name" {
  value = aws_s3_bucket.order_audit.bucket
}

output "lambda_role_arn" {
  value = aws_iam_role.order_audit_lambda.arn
}
