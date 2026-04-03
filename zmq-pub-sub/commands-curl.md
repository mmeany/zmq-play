### ZMQ Play Utility Commands (curl)

This document provides examples of how to interact with the ZMQ Play Utility endpoints using `curl`.

The application is assumed to be running at `http://localhost:8088`.

---

### Register a New One-Shot Publisher

This command registers a new ZeroMQ publisher bound to a specific TCP address.

```bash
curl -X POST http://localhost:8088/register-publisher \
     -H "Content-Type: application/json" \
     -d '{
           "name": "One-shot publisher",
           "address": "tcp://*:5557"
         }'
```

---

### Register a New Subscriber

This command registers a subscriber that connects to a publisher's address. It will automatically save received messages
to the configured output directory.

```bash
curl -X POST http://localhost:8088/register-subscriber \
     -H "Content-Type: application/json" \
     -d '{
           "name": "Subscriber 1",
           "address": "tcp://localhost:5557",
           "binary": false
         }'
```

---

### Register a Binary Subscriber

Similar to a regular subscriber, but configured to treat and save received messages as raw binary data.

```bash
curl -X POST http://localhost:8088/register-subscriber \
     -H "Content-Type: application/json" \
     -d '{
           "name": "Binary Subscriber",
           "address": "tcp://localhost:5557",
           "binary": true
         }'
```

---

### Publish a Message

Sends a message via a registered publisher (one-shot or periodic) on a specific topic.

```bash
curl -X POST http://localhost:8088/publish \
     -H "Content-Type: application/json" \
     -d '{
           "publisherName": "One-shot publisher",
           "topic": "system",
           "message": "Hello from curl!"
         }'
```

---

### Register a Periodic Publisher

Registers a publisher that automatically sends a specified message at fixed intervals.

```bash
curl -X POST http://localhost:8088/register-periodic-publisher \
     -H "Content-Type: application/json" \
     -d '{
           "name": "periodic publisher 1",
           "address": "tcp://*:5556",
           "topic": "heartbeat",
           "message": "{\"name\":\"MyApp\",\"isMain\":true}",
           "period": 2000
         }'
```

---

### Update a Periodic Message

Updates the content of the message being sent by an existing periodic publisher.

```bash
curl -X POST http://localhost:8088/update-periodic-message \
     -H "Content-Type: application/json" \
     -d '{
           "name": "periodic publisher 1",
           "message": "{\"name\":\"MyApp\",\"isMain\":false}"
         }'
```

---

### Register a Monitored Subscriber

Registers a subscriber with a watchdog timer. It expects messages on a specific topic within a certain period. If
messages fail to arrive, an application event is raised.

```bash
curl -X POST http://localhost:8088/register-monitored-subscriber \
     -H "Content-Type: application/json" \
     -d '{
           "name": "monitored subscriber",
           "address": "tcp://localhost:5556",
           "topic": "heartbeat",
           "watchdogPeriod": 1000,
           "failureThreshold": 2,
           "binary": false
         }'
```

---

### Publish a Single File

Publishes the content of a specific file via a registered publisher.

```bash
curl -X POST http://localhost:8088/publish-files \
     -H "Content-Type: application/json" \
     -d '{
           "publisherName": "One-shot publisher",
           "topic": "legal",
           "file": "./LICENSE",
           "binary": false
         }'
```

---

### Publish All Files in a Directory as Binary

Publishes all files within a specified directory via a registered publisher, with a delay between each file.

```bash
curl -X POST http://localhost:8088/publish-files \
     -H "Content-Type: application/json" \
     -d '{
           "publisherName": "One-shot publisher",
           "topic": "archive",
           "directory": ".developer_files/sample-data/binary",
           "delay": 1000,
           "binary": true
         }'
```

---

### Deregister a Publisher

Deregisters an existing publisher by its name. This works for both one-shot and periodic publishers.

```bash
curl -X POST http://localhost:8088/deregister-publisher \
     -H "Content-Type: application/json" \
     -d '{
           "name": "periodic publisher 1"
         }'
```

---

### Deregister a Subscriber

Deregisters an existing subscriber by its name.

```bash
curl -X POST http://localhost:8088/deregister-subscriber \
     -H "Content-Type: application/json" \
     -d '{
           "name": "Subscriber 1"
         }'
```

---

### Enable or Disable a Periodic Publisher

Enables or disables an existing periodic publisher.

```bash
curl -X POST http://localhost:8088/enable-periodic-publisher \
     -H "Content-Type: application/json" \
     -d '{
           "name": "periodic publisher 1",
           "enabled": false
         }'
```

---

### Update Periodic Publisher Frequency

Updates the interval at which a periodic publisher sends its message.

```bash
curl -X POST http://localhost:8088/update-periodic-frequency \
     -H "Content-Type: application/json" \
     -d '{
           "name": "periodic publisher 1",
           "period": 500
         }'
```

---

### List All Publishers

Returns a list of all registered publishers (one-shot and periodic) and their details.

```bash
curl -X GET http://localhost:8088/list-publishers
```

---

### Execute a Lua script

Executes a Lua script provided in the request body.

```bash
curl -X POST http://localhost:8088/execute-lua \
     -H "Content-Type: application/json" \
     -d '{
           "script": "zmq:registerPublisher('\''lua-pub'\'', '\''tcp://*:5555'\''); zmq:publish('\''lua-pub'\'', '\''lua-topic'\'', '\''Hello from Lua!'\''); return '\''Success from Lua script'\''"
         }'
```

---

### Execute a Lua script from a File

Executes a Lua script loaded from a specified file on the server.

```bash
curl -X POST http://localhost:8088/execute-lua \
     -H "Content-Type: application/json" \
     -d '{
           "fileName": "E:/projects2025/zmq-play/zmq-pub-sub/src/test/resources/integration_test.lua"
         }'
```

---

### Publish a Specific List of Files from a Directory

Publishes a provided list of files from a directory via a registered publisher, with a delay between each file.

```bash
curl -X POST http://localhost:8088/publish-file-list \
     -H "Content-Type: application/json" \
     -d '{
           "publisherName": "Files Publisher 2",
           "topic": "ordered-archive",
           "directory": "E:/projects2025/zmq-play/messages/subscriber_1",
           "files": ["file1.json", "file2.json"],
           "delay": 500,
           "binary": false
         }'
```

---

### Publish All Files in a Directory as Text

Publishes all files within a specified directory via a registered publisher, with a delay between each file.

```bash
curl -X POST http://localhost:8088/publish-files \
     -H "Content-Type: application/json" \
     -d '{
           "publisherName": "One-shot publisher",
           "topic": "archive",
           "directory": ".developer_files/sample-data/text",
           "delay": 1000,
           "binary": false
         }'
```
