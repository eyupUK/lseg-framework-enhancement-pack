import importlib.util
import json
import os
from pathlib import Path

import boto3
from moto import mock_aws

MODULE_PATH = Path(__file__).resolve().parents[1] / "handler.py"
SPEC = importlib.util.spec_from_file_location("order_audit_handler", MODULE_PATH)
handler = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(handler)

BUCKET = "audit-test-bucket"


def _create_bucket() -> None:
    boto3.client("s3", region_name="eu-west-2").create_bucket(
        Bucket=BUCKET,
        CreateBucketConfiguration={"LocationConstraint": "eu-west-2"},
    )


@mock_aws
def test_writes_audit_record_to_s3(monkeypatch):
    monkeypatch.setenv("AWS_DEFAULT_REGION", "eu-west-2")
    monkeypatch.setenv("AUDIT_BUCKET", BUCKET)
    _create_bucket()

    response = handler.lambda_handler(
        {
            "headers": {"x-correlation-id": "corr-42"},
            "body": json.dumps({"eventId": "event-1", "orderId": 42, "status": "CREATED"}),
        },
        None,
    )

    assert response["statusCode"] == 202
    body = json.loads(response["body"])
    stored = boto3.client("s3").get_object(Bucket=BUCKET, Key=body["key"])
    record = json.loads(stored["Body"].read())

    assert record["orderId"] == 42
    assert record["status"] == "CREATED"
    assert record["correlationId"] == "corr-42"
    assert stored["Metadata"]["correlation-id"] == "corr-42"


@mock_aws
def test_rejects_missing_required_fields(monkeypatch):
    monkeypatch.setenv("AWS_DEFAULT_REGION", "eu-west-2")
    monkeypatch.setenv("AUDIT_BUCKET", BUCKET)
    _create_bucket()

    response = handler.lambda_handler({"body": json.dumps({"orderId": 42})}, None)

    assert response["statusCode"] == 400
    assert "required" in json.loads(response["body"])["message"]
