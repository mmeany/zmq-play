# ZMQ Pub-Sub Client

A robust, fully-typed Python 3.12+ client for the ZMQ Pub-Sub Spring Boot service.

## Features

- **Dual Support**: Both Synchronous (`ZmqClient`) and Asynchronous (`AsyncZmqClient`) clients.
- **Pydantic v2**: Deep data validation and automatic camelCase conversion.
- **Robust Error Handling**: Custom exceptions for API errors and validation failures.
- **Configurable**: Easy configuration via environment variables or direct parameters.
- **Logging**: Integrated logging for request tracking and debugging.

## Installation

```bash
pip install .
```

## Quick Start

```python
from zmq_pub_sub_client import ZmqClient

with ZmqClient() as client:
    client.register_publisher("weather-pub", "tcp://*:5555")
    client.publish("weather-pub", "cork", "Rainy, 12°C")
```

## API Reference

Both `ZmqClient` and `AsyncZmqClient` provide the following methods:

| Method                                       | Description                                                     |
|----------------------------------------------|-----------------------------------------------------------------|
| `register_publisher(name, address)`          | Registers a new ZMQ publisher.                                  |
| `list_publishers()`                          | Returns a list of all registered publishers.                    |
| `deregister_publisher(name)`                 | Removes a registered publisher.                                 |
| `deregister_subscriber(name)`                | Removes a registered subscriber.                                |
| `publish(publisher_name, topic, message)`    | Publishes a raw string message.                                 |
| `register_subscriber(name, address, binary)` | Registers a subscriber to listen to a specific address.         |
| `register_periodic_publisher(...)`           | Registers a publisher that sends a message at a fixed interval. |
| `register_monitored_subscriber(...)`         | Registers a subscriber with a watchdog timer.                   |
| `update_periodic_message(name, message)`     | Changes the message of a periodic publisher.                    |
| `enable_periodic_publisher(name, enabled)`   | Starts/Stops a periodic publisher.                              |
| `update_periodic_frequency(name, period)`    | Updates the interval (ms) of a periodic publisher.              |
| `publish_files(publisher_name, topic, ...)`  | Publishes all files in a directory or a single file.            |
| `publish_file_list(publisher_name, ...)`     | Publishes a specific list of files from a directory.            |
| `execute_lua(script, file_name)`             | Executes a Lua script on the server and returns the result.     |

## Example Project

Here is how you might use the client in an automated testing project.

### Project Structure

```
my-test-suite/
├── .env                # Optional config
├── requirements.txt    # includes zmq-pub-sub-client
└── integration_test.py
```

### Synchronous Usage (Typical)

```python
from zmq_pub_sub_client import ZmqClient, ZmqApiError


def run_test_scenario():
    # Configuration is automatically picked up from ZMQ_CLIENT_BASE_URL env var
    with ZmqClient() as client:
        try:
            print("Cleaning up existing publishers...")
            pubs = client.list_publishers()
            for pub in pubs:
                client.deregister_publisher(pub.name)

            print("Setting up test publishers...")
            client.register_publisher("sensor-1", "tcp://*:6001")

            print("Registering periodic heartbeat...")
            client.register_periodic_publisher(
                name="heartbeat",
                address="tcp://*:6002",
                topic="status",
                message="ALIVE",
                period=1000
            )

            print("Publishing test data batch...")
            client.publish_files(
                publisher_name="sensor-1",
                topic="data-ingest",
                directory="/path/to/test/data",
                delay=100
            )

            print("Executing remote Lua diagnostic...")
            lua_res = client.execute_lua(script="return 'System Ready'")
            print(f"Server Status: {lua_res.result}")

        except ZmqApiError as e:
            print(f"API Error: {e}")


if __name__ == "__main__":
    run_test_scenario()
```

### Asynchronous Usage (Advanced)

```python
import asyncio
from zmq_pub_sub_client import AsyncZmqClient


async def run_async_scenario():
    async with AsyncZmqClient() as client:
        await client.register_publisher("async-pub", "tcp://*:7001")
        await client.publish("async-pub", "alerts", "CRITICAL")


if __name__ == "__main__":
    asyncio.run(run_async_scenario())
```

## Configuration

Environment variables (prefixed with `ZMQ_CLIENT_`):

| Variable               | Default                 | Description                    |
|------------------------|-------------------------|--------------------------------|
| `ZMQ_CLIENT_BASE_URL`  | `http://localhost:8080` | Service endpoint.              |
| `ZMQ_CLIENT_TIMEOUT`   | `10.0`                  | Connection timeout in seconds. |
| `ZMQ_CLIENT_LOG_LEVEL` | `INFO`                  | Logger verbosity.              |

## Development

```bash
pip install -e ".[test]"
pytest
```
