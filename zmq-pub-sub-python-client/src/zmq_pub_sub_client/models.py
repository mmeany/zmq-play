from typing import List, Optional

from pydantic import BaseModel, Field, ConfigDict, AliasGenerator
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    """Base model that automatically maps snake_case attributes to camelCase for JSON."""
    model_config = ConfigDict(
        alias_generator=AliasGenerator(
            alias=to_camel,
        ),
        populate_by_name=True,
    )


class SuccessResponse(CamelModel):
    success: bool


class ErrorResponse(CamelModel):
    message: str


class ValidationMessage(CamelModel):
    parameter: str
    reason: str


class ValidationFailedResponse(CamelModel):
    error_code: str
    validation_messages: List[ValidationMessage]


class PublisherRegistrationRequest(CamelModel):
    name: str = Field(min_length=1)
    address: str = Field(min_length=1)


class DeregisterPublisherRequest(CamelModel):
    name: str = Field(min_length=1)


class DeregisterSubscriberRequest(CamelModel):
    name: str = Field(min_length=1)


class PublisherDetails(CamelModel):
    name: str
    type: Optional[str] = None
    address: str
    topic: Optional[str] = None
    message: Optional[str] = None
    period: Optional[int] = None
    enabled: Optional[bool] = None


class PublishRequest(CamelModel):
    publisher_name: str = Field(min_length=1)
    topic: str = Field(min_length=1)
    message: Optional[str] = None


class SubscriberRegistrationRequest(CamelModel):
    name: str = Field(min_length=1)
    address: str = Field(min_length=1)
    binary: bool = False


class PeriodicPublisherRegistrationRequest(CamelModel):
    name: str = Field(min_length=1)
    address: str = Field(min_length=1)
    topic: str = Field(min_length=1)
    message: Optional[str] = None
    period: int = Field(gt=0)


class MonitoredSubscriberRegistrationRequest(CamelModel):
    name: str = Field(min_length=1)
    address: str = Field(min_length=1)
    topic: str = Field(min_length=1)
    watchdog_period: int = Field(gt=0)
    failure_threshold: int = Field(gt=0)
    binary: bool = False


class PeriodicPublisherUpdateRequest(CamelModel):
    name: str = Field(min_length=1)
    message: Optional[str] = None


class PeriodicPublisherStatusRequest(CamelModel):
    name: str = Field(min_length=1)
    enabled: bool


class PeriodicPublisherFrequencyRequest(CamelModel):
    name: str = Field(min_length=1)
    period: int = Field(gt=0)


class PublishFilesRequest(CamelModel):
    publisher_name: str = Field(min_length=1)
    topic: str = Field(min_length=1)
    directory: Optional[str] = None
    file: Optional[str] = None
    delay: int = Field(default=0, ge=0)
    binary: bool = False


class PublishFileListRequest(CamelModel):
    publisher_name: str = Field(min_length=1)
    topic: str = Field(min_length=1)
    directory: str = Field(min_length=1)
    files: List[str] = Field(min_length=1)
    delay: int = Field(default=0, ge=0)
    binary: bool = False


class LuaExecutionRequest(CamelModel):
    script: Optional[str] = None
    file_name: Optional[str] = None


class LuaExecutionResponse(CamelModel):
    result: Optional[str] = None
    error: Optional[str] = None
    success: bool
