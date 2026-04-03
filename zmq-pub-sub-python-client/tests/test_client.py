import pytest
import respx
from httpx import Response
from pydantic import ValidationError

from zmq_pub_sub_client import ZmqClient, ZmqApiError
from zmq_pub_sub_client.settings import settings


@respx.mock
def test_register_publisher_success():
    # Mock the POST request
    respx.post(f"{settings.base_url}/register-publisher").mock(
        return_value=Response(200, json={"success": True})
    )

    with ZmqClient() as client:
        result = client.register_publisher("test-pub", "tcp://*:5555")
        assert result is True


@respx.mock
def test_list_publishers():
    # Mock the GET request
    respx.get(f"{settings.base_url}/list-publishers").mock(
        return_value=Response(200, json=[
            {
                "name": "pub1",
                "type": "PUB",
                "address": "tcp://*:5555"
            }
        ])
    )

    with ZmqClient() as client:
        publishers = client.list_publishers()
        assert len(publishers) == 1
        assert publishers[0].name == "pub1"


@respx.mock
def test_api_error():
    # Mock a 400 error
    respx.post(f"{settings.base_url}/register-publisher").mock(
        return_value=Response(400, json={"message": "Already exists"})
    )

    with ZmqClient() as client:
        with pytest.raises(ZmqApiError) as excinfo:
            client.register_publisher("test-pub", "tcp://*:5555")
        assert "Already exists" in str(excinfo.value)


def test_validation_failed_error():
    with ZmqClient() as client:
        # Empty publisher names fail client-side Pydantic validation before any HTTP request.
        with pytest.raises(ValidationError):
            client.register_publisher("", "tcp://*:5555")


@respx.mock
def test_deregister_subscriber_success():
    # Mock the POST request
    respx.post(f"{settings.base_url}/deregister-subscriber").mock(
        return_value=Response(200, json={"success": True})
    )

    with ZmqClient() as client:
        result = client.deregister_subscriber("test-sub")
        assert result is True


@respx.mock
def test_server_validation_failed_parsing():
    # This test verifies that if the server returns a validation error, 
    # the client correctly parses the ValidationFailedResponse.
    # We'll use a valid request from the client's perspective to reach the server.
    respx.post(f"{settings.base_url}/register-publisher").mock(
        return_value=Response(400, json={
            "errorCode": "VALIDATION_ERROR",
            "validationMessages": [
                {"parameter": "address", "reason": "invalid format"}
            ]
        })
    )

    with ZmqClient() as client:
        with pytest.raises(ZmqApiError) as excinfo:
            # "valid" name and address from pydantic's perspective, but "invalid" for server
            client.register_publisher("test-pub", "invalid-address")
        assert "Validation failed: VALIDATION_ERROR" in str(excinfo.value)
        assert "invalid format" in str(excinfo.value)
