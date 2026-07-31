"""AWS Lambda handler that persists an immutable order audit record in S3."""

from __future__ import annotations

import json
import logging
import os
from datetime import datetime, timezone
from typing import Any
from uuid import uuid4

import boto3

LOGGER = logging.getLogger()
LOGGER.setLevel(logging.INFO)


def _response(status_code: int, body: dict[str, Any]) -> dict[str, Any]:
    return {
        "statusCode": status_code,
        "headers": {"Content-Type": "application/json"},
        "body": json.dumps(body),
    }


def _parse_payload(event: dict[str, Any]) -> dict[str, Any]:
    body = event.get("body", event)
    if isinstance(body, str):
        body = json.loads(body)
    if not isinstance(body, dict):
        raise TypeError("Request body must be a JSON object")
    return body


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    bucket = os.environ.get("AUDIT_BUCKET", "")
    if not bucket:
        LOGGER.error(json.dumps({"event": "configuration_error", "missing": "AUDIT_BUCKET"}))
        return _response(500, {"message": "Service configuration error"})

    try:
        payload = _parse_payload(event)
    except (TypeError, ValueError, json.JSONDecodeError) as exc:
        return _response(400, {"message": str(exc)})

    order_id = payload.get("orderId")
    status = payload.get("status")
    if order_id is None or not status:
        return _response(400, {"message": "orderId and status are required"})

    headers = event.get("headers") or {}
    correlation_id = (
        headers.get("x-correlation-id")
        or headers.get("X-Correlation-Id")
        or str(uuid4())
    )
    event_id = payload.get("eventId") or str(uuid4())

    audit_record = {
        "eventId": event_id,
        "orderId": order_id,
        "status": status,
        "correlationId": correlation_id,
        "recordedAt": datetime.now(timezone.utc).isoformat(),
    }
    key = f"orders/{order_id}/{event_id}.json"

    boto3.client("s3").put_object(
        Bucket=bucket,
        Key=key,
        Body=json.dumps(audit_record).encode("utf-8"),
        ContentType="application/json",
        Metadata={"correlation-id": correlation_id},
    )

    LOGGER.info(json.dumps({"event": "order_audit_written", "key": key,
                            "correlationId": correlation_id}))
    return _response(202, {"eventId": event_id, "key": key})
