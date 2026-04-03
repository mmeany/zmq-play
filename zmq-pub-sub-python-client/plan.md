# Plan: Python Client for ZMQ Pub-Sub Service

This plan outlines the implementation of a Python 3 client for the ZMQ Pub-Sub Spring Boot application. The client will
be designed for use in automated testing and eventual PyPI publication.

## 1. Project Structure

The project will be organized following modern Python packaging standards:

```
zmq-pub-sub-python-client/
├── pyproject.toml           # Build system and package metadata
├── README.md                # Documentation
├── src/
│   └── zmq_pub_sub_client/
│       ├── __init__.py      # Package export
│       ├── client.py        # Main API client (ZmqClient)
│       ├── models.py        # Pydantic models for requests/responses
│       ├── settings.py      # Pydantic Settings for configuration
│       ├── exceptions.py    # Custom exception classes
│       └── logger.py        # Logging configuration
└── tests/
    └── test_client.py       # Basic integration/unit tests
```

## 2. Technical Stack

- **Language**: Python 3.12+
- **HTTP Client**: `httpx` (supports both sync and async, modern API)
- **Data Validation**: `pydantic` v2
- **Configuration**: `pydantic-settings`
- **Build Tool**: `hatchling` (modern, lightweight PEP 517 build backend)

## 3. Implementation Details

### A. Data Models (`models.py`)

- Define Pydantic models for all Java request/response classes found in `Controller.java`.
- Use Pydantic's `Field` for validation (e.g., `min_length`, `gt=0`).
- Ensure all fields match the Java backend's expectations (naming, types).

### B. Configuration (`settings.py`)

- Use `BaseSettings` from `pydantic-settings`.
- Configurable parameters: `BASE_URL`, `TIMEOUT`, `LOG_LEVEL`.
- Support for environment variables (e.g., `ZMQ_CLIENT_BASE_URL`).

### C. Client Implementation (`client.py`)

- Class `ZmqClient` with methods for each endpoint:
    - `register_publisher(name: str, address: str) -> bool`
    - `list_publishers() -> List[PublisherDetails]`
    - `deregister_publisher(name: str) -> bool`
    - `publish(publisher_name: str, topic: str, message: Optional[str] = None) -> bool`
    - `register_subscriber(...)`
    - `register_periodic_publisher(...)`
    - `register_monitored_subscriber(...)`
    - `update_periodic_message(...)`
    - `enable_periodic_publisher(...)`
    - `update_periodic_frequency(...)`
    - `publish_files(...)`
    - `publish_file_list(...)`
    - `execute_lua(script: Optional[str] = None, file_name: Optional[str] = None) -> LuaExecutionResponse`
- Robust error handling: raise custom exceptions on HTTP errors or validation failures.
- Automatic Pydantic model instantiation from API responses.

### D. Logging (`logger.py`)

- Configurable logger with standard formatting.
- Integrated into the `ZmqClient` to log requests and errors.

## 4. Verification Plan

- Create a test suite in `tests/` that:
    - Mocks the API for unit testing.
    - (Optional) Provides a way to run against a live instance if available.
- Validate that Pydantic models correctly reject invalid input before sending requests.

## 5. Deployment Readiness

- Configure `pyproject.toml` with:
    - Package dependencies (`httpx`, `pydantic`, `pydantic-settings`).
    - Project metadata (author, version, description).
    - Entry points if needed.

---
**Next Steps:**

1. I will wait for your feedback on this plan.
2. Once approved, I will proceed with creating the file structure and implementing the components.
