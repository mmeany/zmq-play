# ZeroMQ Utility

This utility is a Spring Boot application for exploring ZeroMQ (JeroMQ) patterns for publishing and subscribing. It
provides a RESTful API to dynamically manage ZMQ publishers and subscribers, including support for periodic publishing,
Lua scripting, and automatic message logging.

## Key Features

- **Dynamic Publisher Management**: Create and deregister ZMQ PUB sockets bound to specific addresses via HTTP.
- **Message Publishing**: Send messages to any registered publisher (one-shot or periodic). Supports JSON payloads.
- **File Publishing**: Publish single files, entire directories, or specific file lists via a registered publisher and
  topic, with optional delays between files.
- **Dynamic Subscriber Registration**: Create ZMQ SUB sockets that connect to specified addresses.
- **Binary Support**: Subscribers can be configured to save messages as raw binary or UTF-8 text.
- **Automatic Logging**: Subscribers automatically listen to all topics and save received messages as files in a
  configurable output directory.
- **Periodic Publishing**: Register publishers that automatically send a specific message at a defined interval.
- **Control over Periodic Publishers**: Enable/disable publishers, update messages, or change frequencies on the fly.
- **Monitored Subscribers**: Register subscribers with watchdog timers to detect if expected messages (e.g., heartbeats)
  fail to arrive within a threshold.
- **Lua Scripting**: Execute complex sequences of operations (registering, publishing, sleeping) via a built-in Lua
  engine.
- **Thread Isolation**: Each subscriber and periodic publisher runs in its own dedicated thread.

## API Endpoints

The utility exposes the following endpoints:

| Method | Endpoint                         | Description                                                              |
|:-------|:---------------------------------|:-------------------------------------------------------------------------|
| `POST` | `/register-publisher`            | Registers a new one-shot publisher.                                      |
| `GET`  | `/list-publishers`               | Returns a list of all registered publishers and their details.           |
| `POST` | `/deregister-publisher`          | Stops and removes a registered publisher (one-shot or periodic).         |
| `POST` | `/deregister-subscriber`         | Stops and removes a registered subscriber.                               |
| `POST` | `/publish`                       | Publishes a message to a registered publisher.                           |
| `POST` | `/register-subscriber`           | Registers a new subscriber that logs all incoming messages to disk.      |
| `POST` | `/register-monitored-subscriber` | Registers a subscriber with a watchdog timer to monitor message arrival. |
| `POST` | `/register-periodic-publisher`   | Registers a publisher that sends a message at fixed intervals.           |
| `POST` | `/update-periodic-message`       | Updates the message content for an existing periodic publisher.          |
| `POST` | `/enable-periodic-publisher`     | Enables or disables an existing periodic publisher.                      |
| `POST` | `/update-periodic-frequency`     | Updates the interval for a periodic publisher.                           |
| `POST` | `/publish-files`                 | Publishes a single file or all files in a directory via a publisher.     |
| `POST` | `/publish-file-list`             | Publishes a specific list of files from a directory.                     |
| `POST` | `/execute-lua`                   | Executes a Lua script (embedded or from a file).                         |

### Swagger Documentation

When the application is running, you can access the interactive Swagger UI at:
[http://localhost:8088/swagger-ui.html](http://localhost:8088/swagger-ui.html)

## Usage Examples

Comprehensive examples for interacting with the API are provided in the following formats:

- **[REST Client (IntelliJ)](./commands.http)**: Ready-to-use HTTP requests.
- **[PowerShell](./commands-powershell.md)**: Scripts using `Invoke-RestMethod`.
- **[curl (Bash)](./commands-curl.md)**: Standard command-line examples.
- **[Lua scripting](./commands-lua.md)**: Examples using Lua scripts for advanced operations.

## Configuration

The application can be configured via `src/main/resources/application.yml`. Key properties include:

- `server.port`: The port on which the REST API runs (default: `8088`).
- `output-directory`: The root directory where subscriber messages are saved (default: `messages`).
- `output-directory-clear-on-startup`: When `true`, the application will delete the entire output directory (if it
  exists) and recreate it during startup. Default: `false`.

## Getting Started

To build and run the application:

```powershell
.\gradlew build
.\gradlew bootRun
```
