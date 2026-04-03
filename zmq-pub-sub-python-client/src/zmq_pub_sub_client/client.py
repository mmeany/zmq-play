from typing import Any, List, Optional, TypeVar, Union

import httpx
from pydantic import BaseModel, ValidationError, TypeAdapter

from .exceptions import ZmqApiError, ZmqValidationError
from .logger import logger
from .models import (
    PublisherRegistrationRequest, DeregisterPublisherRequest, DeregisterSubscriberRequest, PublisherDetails,
    PublishRequest, SubscriberRegistrationRequest, PeriodicPublisherRegistrationRequest,
    MonitoredSubscriberRegistrationRequest, PeriodicPublisherUpdateRequest,
    PeriodicPublisherStatusRequest, PeriodicPublisherFrequencyRequest,
    PublishFilesRequest, PublishFileListRequest, LuaExecutionRequest,
    LuaExecutionResponse, SuccessResponse, ValidationFailedResponse
)
from .settings import settings as default_settings

T = TypeVar("T")


class BaseZmqClient:
    """Base functionality for ZMQ Pub-Sub clients."""

    def __init__(self, base_url: str = None, timeout: float = None):
        self.base_url = (base_url or default_settings.base_url).rstrip("/")
        self.timeout = timeout or default_settings.timeout

    def _prepare_payload(self, request: Union[BaseModel, dict]) -> dict:
        """Converts a model or dict to a camelCase dict for the API."""
        if isinstance(request, BaseModel):
            return request.model_dump(by_alias=True, exclude_none=True)
        return request

    def _parse_error(self, response: httpx.Response) -> str:
        """Attempts to parse error details from the response."""
        try:
            error_data = response.json()
            if isinstance(error_data, dict):
                if "validationMessages" in error_data:
                    vf = ValidationFailedResponse.model_validate(error_data)
                    return f"Validation failed: {vf.error_code} - {vf.validation_messages}"
                if "message" in error_data:
                    return error_data["message"]
            return f"Error response: {error_data}"
        except Exception:
            return f"HTTP {response.status_code}: {response.text}"

    def _validate_response(self, response: httpx.Response, expected_type: Any) -> Any:
        """Validates the response against the expected type/model."""
        try:
            response.raise_for_status()
            data = response.json()
            adapter = TypeAdapter(expected_type)
            return adapter.validate_python(data)
        except httpx.HTTPStatusError as e:
            msg = self._parse_error(e.response)
            logger.error(f"API Error [{e.response.status_code}]: {msg}")
            raise ZmqApiError(msg, status_code=e.response.status_code,
                              details=e.response.json() if "application/json" in e.response.headers.get("Content-Type",
                                                                                                        "") else None) from e
        except ValidationError as e:
            logger.error(f"Response validation error: {e}")
            raise ZmqValidationError(f"Invalid response format: {e}") from e
        except Exception as e:
            logger.error(f"Unexpected error: {e}")
            raise ZmqApiError(f"Unexpected error: {str(e)}") from e


class ZmqClient(BaseZmqClient):
    """Synchronous client for the ZMQ Pub-Sub service."""

    def __init__(self, base_url: str = None, timeout: float = None, client: httpx.Client = None):
        super().__init__(base_url, timeout)
        self.client = client or httpx.Client(base_url=self.base_url, timeout=self.timeout)

    def register_publisher(self, name: str, address: str) -> bool:
        """Registers a new publisher."""
        req = PublisherRegistrationRequest(name=name, address=address)
        resp = self.client.post("/register-publisher", json=self._prepare_payload(req))
        return self._validate_response(resp, SuccessResponse).success

    def list_publishers(self) -> List[PublisherDetails]:
        """Lists all registered publishers."""
        resp = self.client.get("/list-publishers")
        return self._validate_response(resp, List[PublisherDetails])

    def deregister_publisher(self, name: str) -> bool:
        """Deregisters an existing publisher."""
        req = DeregisterPublisherRequest(name=name)
        resp = self.client.post("/deregister-publisher", json=self._prepare_payload(req))
        return self._validate_response(resp, SuccessResponse).success

    def deregister_subscriber(self, name: str) -> bool:
        """Deregisters an existing subscriber."""
        req = DeregisterSubscriberRequest(name=name)
        resp = self.client.post("/deregister-subscriber", json=self._prepare_payload(req))
        return self._validate_response(resp, SuccessResponse).success

    def publish(self, publisher_name: str, topic: str, message: Optional[str] = None) -> bool:
        """Publishes a message on a given topic."""
        req = PublishRequest(publisher_name=publisher_name, topic=topic, message=message)
        resp = self.client.post("/publish", json=self._prepare_payload(req))
        return self._validate_response(resp, SuccessResponse).success

    def register_subscriber(self, name: str, address: str, binary: bool = False) -> bool:
        """Registers a new subscriber."""
        req = SubscriberRegistrationRequest(name=name, address=address, binary=binary)
        resp = self.client.post("/register-subscriber", json=self._prepare_payload(req))
        return self._validate_response(resp, SuccessResponse).success

    def register_periodic_publisher(self, name: str, address: str, topic: str,
                                    message: Optional[str], period: int) -> bool:
        """Registers a new periodic publisher."""
        req = PeriodicPublisherRegistrationRequest(name=name, address=address, topic=topic, message=message,
                                                   period=period)
        resp = self.client.post("/register-periodic-publisher", json=self._prepare_payload(req))
        return self._validate_response(resp, SuccessResponse).success

    def register_monitored_subscriber(self, name: str, address: str, topic: str,
                                      watchdog_period: int, failure_threshold: int, binary: bool = False) -> bool:
        """Registers a new monitored subscriber."""
        req = MonitoredSubscriberRegistrationRequest(
            name=name, address=address, topic=topic,
            watchdog_period=watchdog_period, failure_threshold=failure_threshold, binary=binary
        )
        resp = self.client.post("/register-monitored-subscriber", json=self._prepare_payload(req))
        return self._validate_response(resp, SuccessResponse).success

    def update_periodic_message(self, name: str, message: Optional[str]) -> bool:
        """Updates the message of a periodic publisher."""
        req = PeriodicPublisherUpdateRequest(name=name, message=message)
        resp = self.client.post("/update-periodic-message", json=self._prepare_payload(req))
        return self._validate_response(resp, SuccessResponse).success

    def enable_periodic_publisher(self, name: str, enabled: bool) -> bool:
        """Enables or disables a periodic publisher."""
        req = PeriodicPublisherStatusRequest(name=name, enabled=enabled)
        resp = self.client.post("/enable-periodic-publisher", json=self._prepare_payload(req))
        return self._validate_response(resp, SuccessResponse).success

    def update_periodic_frequency(self, name: str, period: int) -> bool:
        """Updates the frequency of a periodic publisher."""
        req = PeriodicPublisherFrequencyRequest(name=name, period=period)
        resp = self.client.post("/update-periodic-frequency", json=self._prepare_payload(req))
        return self._validate_response(resp, SuccessResponse).success

    def publish_files(self, publisher_name: str, topic: str, directory: str = None,
                      file: str = None, delay: int = 0, binary: bool = False) -> bool:
        """Publishes file(s) on a given topic."""
        req = PublishFilesRequest(
            publisher_name=publisher_name, topic=topic, directory=directory, file=file, delay=delay, binary=binary
        )
        resp = self.client.post("/publish-files", json=self._prepare_payload(req))
        return self._validate_response(resp, SuccessResponse).success

    def publish_file_list(self, publisher_name: str, topic: str, directory: str,
                          files: List[str], delay: int = 0, binary: bool = False) -> bool:
        """Publishes a specific list of files."""
        req = PublishFileListRequest(
            publisher_name=publisher_name, topic=topic, directory=directory, files=files, delay=delay, binary=binary
        )
        resp = self.client.post("/publish-file-list", json=self._prepare_payload(req))
        return self._validate_response(resp, SuccessResponse).success

    def execute_lua(self, script: Optional[str] = None, file_name: Optional[str] = None) -> LuaExecutionResponse:
        """Executes a Lua script."""
        req = LuaExecutionRequest(script=script, file_name=file_name)
        resp = self.client.post("/execute-lua", json=self._prepare_payload(req))
        return self._validate_response(resp, LuaExecutionResponse)

    def close(self):
        """Closes the underlying HTTP client."""
        self.client.close()

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()


class AsyncZmqClient(BaseZmqClient):
    """Asynchronous client for the ZMQ Pub-Sub service."""

    def __init__(self, base_url: str = None, timeout: float = None, client: httpx.AsyncClient = None):
        super().__init__(base_url, timeout)
        self.client = client or httpx.AsyncClient(base_url=self.base_url, timeout=self.timeout)

    async def register_publisher(self, name: str, address: str) -> bool:
        """Registers a new publisher."""
        req = PublisherRegistrationRequest(name=name, address=address)
        resp = await self.client.post("/register-publisher", json=self._prepare_payload(req))
        return self._validate_response(resp, SuccessResponse).success

    async def list_publishers(self) -> List[PublisherDetails]:
        """Lists all registered publishers."""
        resp = await self.client.get("/list-publishers")
        return self._validate_response(resp, List[PublisherDetails])

    async def deregister_publisher(self, name: str) -> bool:
        """Deregisters an existing publisher."""
        req = DeregisterPublisherRequest(name=name)
        resp = await self.client.post("/deregister-publisher", json=self._prepare_payload(req))
        return self._validate_response(resp, SuccessResponse).success

    async def deregister_subscriber(self, name: str) -> bool:
        """Deregisters an existing subscriber."""
        req = DeregisterSubscriberRequest(name=name)
        resp = await self.client.post("/deregister-subscriber", json=self._prepare_payload(req))
        return self._validate_response(resp, SuccessResponse).success

    async def publish(self, publisher_name: str, topic: str, message: Optional[str] = None) -> bool:
        """Publishes a message on a given topic."""
        req = PublishRequest(publisher_name=publisher_name, topic=topic, message=message)
        resp = await self.client.post("/publish", json=self._prepare_payload(req))
        return self._validate_response(resp, SuccessResponse).success

    async def register_subscriber(self, name: str, address: str, binary: bool = False) -> bool:
        """Registers a new subscriber."""
        req = SubscriberRegistrationRequest(name=name, address=address, binary=binary)
        resp = await self.client.post("/register-subscriber", json=self._prepare_payload(req))
        return self._validate_response(resp, SuccessResponse).success

    async def register_periodic_publisher(self, name: str, address: str, topic: str,
                                          message: Optional[str], period: int) -> bool:
        """Registers a new periodic publisher."""
        req = PeriodicPublisherRegistrationRequest(name=name, address=address, topic=topic, message=message,
                                                   period=period)
        resp = await self.client.post("/register-periodic-publisher", json=self._prepare_payload(req))
        return self._validate_response(resp, SuccessResponse).success

    async def register_monitored_subscriber(self, name: str, address: str, topic: str,
                                            watchdog_period: int, failure_threshold: int, binary: bool = False) -> bool:
        """Registers a new monitored subscriber."""
        req = MonitoredSubscriberRegistrationRequest(
            name=name, address=address, topic=topic,
            watchdog_period=watchdog_period, failure_threshold=failure_threshold, binary=binary
        )
        resp = await self.client.post("/register-monitored-subscriber", json=self._prepare_payload(req))
        return self._validate_response(resp, SuccessResponse).success

    async def update_periodic_message(self, name: str, message: Optional[str]) -> bool:
        """Updates the message of a periodic publisher."""
        req = PeriodicPublisherUpdateRequest(name=name, message=message)
        resp = await self.client.post("/update-periodic-message", json=self._prepare_payload(req))
        return self._validate_response(resp, SuccessResponse).success

    async def enable_periodic_publisher(self, name: str, enabled: bool) -> bool:
        """Enables or disables a periodic publisher."""
        req = PeriodicPublisherStatusRequest(name=name, enabled=enabled)
        resp = await self.client.post("/enable-periodic-publisher", json=self._prepare_payload(req))
        return self._validate_response(resp, SuccessResponse).success

    async def update_periodic_frequency(self, name: str, period: int) -> bool:
        """Updates the frequency of a periodic publisher."""
        req = PeriodicPublisherFrequencyRequest(name=name, period=period)
        resp = await self.client.post("/update-periodic-frequency", json=self._prepare_payload(req))
        return self._validate_response(resp, SuccessResponse).success

    async def publish_files(self, publisher_name: str, topic: str, directory: str = None,
                            file: str = None, delay: int = 0, binary: bool = False) -> bool:
        """Publishes file(s) on a given topic."""
        req = PublishFilesRequest(
            publisher_name=publisher_name, topic=topic, directory=directory, file=file, delay=delay, binary=binary
        )
        resp = await self.client.post("/publish-files", json=self._prepare_payload(req))
        return self._validate_response(resp, SuccessResponse).success

    async def publish_file_list(self, publisher_name: str, topic: str, directory: str,
                                files: List[str], delay: int = 0, binary: bool = False) -> bool:
        """Publishes a specific list of files."""
        req = PublishFileListRequest(
            publisher_name=publisher_name, topic=topic, directory=directory, files=files, delay=delay, binary=binary
        )
        resp = await self.client.post("/publish-file-list", json=self._prepare_payload(req))
        return self._validate_response(resp, SuccessResponse).success

    async def execute_lua(self, script: Optional[str] = None, file_name: Optional[str] = None) -> LuaExecutionResponse:
        """Executes a Lua script."""
        req = LuaExecutionRequest(script=script, file_name=file_name)
        resp = await self.client.post("/execute-lua", json=self._prepare_payload(req))
        return self._validate_response(resp, LuaExecutionResponse)

    async def close(self):
        """Closes the underlying HTTP client."""
        await self.client.aclose()

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        await self.close()
