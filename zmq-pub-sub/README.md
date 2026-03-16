# ZeroMQ Utility

This utility is a Spring Boot application for exploring ZeroMQ (JeroMQ) patterns for publishing and subscribing. It
provides a RESTful API to dynamically manage ZMQ publishers and subscribers, including support for periodic publishing
and automatic message logging.

## Key Features

- **Dynamic Publisher Registration**: Create new ZMQ PUB sockets bound to specific addresses via HTTP.
- **Message Publishing**: Send messages to any registered publisher (one-shot or periodic). Supports JSON payloads.
- **File Publishing**: Publish single files or entire directories via a registered publisher and topic, with optional
  delays between files.
- **Dynamic Subscriber Registration**: Create ZMQ SUB sockets that connect to specified addresses.
- **Binary Support**: Subscribers can be configured to save messages as raw binary or UTF-8 text.
- **Automatic Logging**: Subscribers automatically listen to all topics and save received messages as `.json` files in a
  configurable output directory.
- **Periodic Publishing**: Register publishers that automatically send a specific message at a defined interval.
- **Dynamic Updates**: Change the message being sent by a periodic publisher on the fly.
- **Monitored Subscribers**: Register subscribers with watchdog timers to detect if expected messages (e.g., heartbeats)
  fail to arrive within a threshold.
- **Thread Isolation**: Each subscriber and periodic publisher runs in its own dedicated thread.

## API Endpoints

The utility exposes the following POST endpoints:

| Endpoint                         | Description                                                              |
|:---------------------------------|:-------------------------------------------------------------------------|
| `/register-publisher`            | Registers a new one-shot publisher.                                      |
| `/publish`                       | Publishes a message to a registered publisher (one-shot or periodic).    |
| `/register-subscriber`           | Registers a new subscriber that logs all incoming messages to disk.      |
| `/register-periodic-publisher`   | Registers a publisher that sends a message at fixed intervals.           |
| `/register-monitored-subscriber` | Registers a subscriber with a watchdog timer to monitor message arrival. |
| `/update-periodic-message`       | Updates the message content for an existing periodic publisher.          |
| `/publish-files`                 | Publishes a single file or all files in a directory via a publisher.     |

### Swagger Documentation

When the application is running, you can access the interactive Swagger UI at:
[http://localhost:8088/swagger-ui.html](http://localhost:8088/swagger-ui.html)

## Usage Examples

For practical examples of how to interact with these endpoints, please refer to the [commands.http](./commands.http)
file. This file contains ready-to-use HTTP requests for:

- Creating periodic publishers
- Registering subscribers
- Updating periodic messages
- One-shot and periodic publishing via `/publish`
- Registering monitored subscribers
- Publishing files and directories via `/publish-files`
- Registering binary subscribers

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
