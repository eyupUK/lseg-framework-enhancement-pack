import importlib.util
import json
from pathlib import Path
from uuid import UUID

import boto3
from moto import mock_aws

MODULE_PATH = Path(__file__).resolve().parents[1] / "handler.py"
SPEC = importlib.util.spec_from_file_location("order_audit_handler_edge_cases", MODULE_PATH)
handler = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(handler)

BUCKET = "audit-edge-case-bucket"


def _create_bucket() -> None:
    boto3.client("s3", region_name="eu-west-2").create_bucket(
        Bucket=BUCKET,
        CreateBucketConfiguration={"LocationConstraint": "eu-west-2"},
    )


def test_returns_500_when_audit_bucket_is_not_configured(monkeypatch):
    monkeypatch.delenv("AUDIT_BUCKET", raising=False)

    response = handler.lambda_handler(
        {"body": json.dumps({"orderId": 42, "status": "CREATED"})},
        None,
    )

    assert response["statusCode"] == 500
    assert json.loads(response["body"]) == {
        "message": "Service configuration error"
    }


def test_returns_400_for_malformed_json(monkeypatch):
    monkeypatch.setenv("AUDIT_BUCKET", BUCKET)

    response = handler.lambda_handler({"body": "{not-json"}, None)

    assert response["statusCode"] == 400


@mock_aws
def test_accepts_the_canonical_header_case(monkeypatch):
    monkeypatch.setenv("AWS_DEFAULT_REGION", "eu-west-2")
    monkeypatch.setenv("AUDIT_BUCKET", BUCKET)
    _create_bucket()

    response = handler.lambda_handler(
        {
            "headers": {"X-Correlation-Id": "corr-uppercase"},
            "body": json.dumps({
                "eventId": "event-uppercase",
                "orderId": 51,
                "status": "CREATED",
            }),
        },
        None,
    )

    assert response["statusCode"] == 202
    stored = boto3.client("s3").get_object(
        Bucket=BUCKET,
        Key=json.loads(response["body"])["key"],
    )
    record = json.loads(stored["Body"].read())
    assert record["correlationId"] == "corr-uppercase"


@mock_aws
def test_generates_a_valid_correlation_id_when_header_is_absent(monkeypatch):
    monkeypatch.setenv("AWS_DEFAULT_REGION", "eu-west-2")
    monkeypatch.setenv("AUDIT_BUCKET", BUCKET)
    _create_bucket()

    response = handler.lambda_handler(
        {
            "body": json.dumps({
                "eventId": "event-generated-correlation",
                "orderId": 52,
                "status": "CREATED",
            }),
        },
        None,
    )

    stored = boto3.client("s3").get_object(
        Bucket=BUCKET,
        Key=json.loads(response["body"])["key"],
    )
    record = json.loads(stored["Body"].read())

    assert str(UUID(record["correlationId"])) == record["correlationId"]
