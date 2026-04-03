import pytest
import respx
from httpx import Response
from zmq_pub_sub_client import ZmqClient, ZmqApiError

@respx.mock
def test_register_publisher_success():
    # Mock the POST request
    respx.post("http://localhost:8080/register-publisher").mock(
        return_value=Response(200, json={"success": True})
    )
    
    with ZmqClient() as client:
        result = client.register_publisher("test-pub", "tcp://*:5555")
        assert result is True

@respx.mock
def test_list_publishers():
    # Mock the GET request
    respx.get("http://localhost:8080/list-publishers").mock(
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
    respx.post("http://localhost:8080/register-publisher").mock(
        return_value=Response(400, json={"message": "Already exists"})
    )
    
    with ZmqClient() as client:
        with pytest.raises(ZmqApiError) as excinfo:
            client.register_publisher("test-pub", "tcp://*:5555")
        assert "Already exists" in str(excinfo.value)

@respx.mock
def test_validation_failed_error():
    # Mock a validation error
    respx.post("http://localhost:8080/register-publisher").mock(
        return_value=Response(400, json={
            "errorCode": "VALIDATION_ERROR",
            "validationMessages": [
                {"parameter": "name", "reason": "must not be blank"}
            ]
        })
    )
    
    with ZmqClient() as client:
        with pytest.raises(ZmqApiError) as excinfo:
            client.register_publisher("", "tcp://*:5555")
        assert "Validation failed" in str(excinfo.value)
