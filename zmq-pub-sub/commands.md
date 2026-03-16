### ZMQ Play Utility Commands (PowerShell)

This document provides examples of how to interact with the ZMQ Play Utility endpoints using PowerShell's
`Invoke-RestMethod`. From IntelliJ these scripts can be executed directly within the IDE, providing PowerShell is the
default terminal, from this file!

The application is assumed to be running at `http://localhost:8088`.

---

### Register a New One-Shot Publisher

This command registers a new ZeroMQ publisher bound to a specific TCP address.

```shell
$$body = @{
    name    = "One-shot publisher";
    address = "tcp://*:5557";
} | ConvertTo-Json;

Invoke-RestMethod -Uri "http://localhost:8088/register-publisher" -Method Post -ContentType "application/json" -Body $body
```

---

### Register a New Subscriber

This command registers a subscriber that connects to a publisher's address. It will automatically save received messages
to the configured output directory.

```shell
$body = @{
    name    = "Subscriber 1"
    address = "tcp://localhost:5557"
    binary  = $false
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8088/register-subscriber" -Method Post -ContentType "application/json" -Body $body
```

---

### Register a Binary Subscriber

Similar to a regular subscriber, but configured to treat and save received messages as raw binary data.

```shell

$$body = @{
    name    = "Binary Subscriber";
    address = "tcp://localhost:5557";
    binary  = $true;
} | ConvertTo-Json;

Invoke-RestMethod -Uri "http://localhost:8088/register-subscriber" -Method Post -ContentType "application/json" -Body $body
```

---

### Publish a Message

Sends a message via a registered publisher (one-shot or periodic) on a specific topic.

```shell
$$body = @{
    publisherName = "One-shot publisher";
    topic         = "system";
    message       = "Hello from PowerShell!";
} | ConvertTo-Json;

Invoke-RestMethod -Uri "http://localhost:8088/publish" -Method Post -ContentType "application/json" -Body $body
```

---

### Register a Periodic Publisher

Registers a publisher that automatically sends a specified message at fixed intervals.

```shell
$body = @{
    name    = "periodic publisher 1"
    address = "tcp://*:5556"
    topic   = "heartbeat"
    message = '{"name":"MyApp","isMain":true}'
    period  = 2000
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8088/register-periodic-publisher" -Method Post -ContentType "application/json" -Body $body
```

---

### Update a Periodic Message

Updates the content of the message being sent by an existing periodic publisher.

```shell
$body = @{
    name    = "periodic publisher 1"
    message = '{"name":"MyApp","isMain":false}'
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8088/update-periodic-message" -Method Post -ContentType "application/json" -Body $body
```

---

### Register a Monitored Subscriber

Registers a subscriber with a watchdog timer. It expects messages on a specific topic within a certain period. If
messages fail to arrive, an application event is raised.

```shell
$body = @{
    name             = "monitored subscriber"
    address          = "tcp://localhost:5556"
    topic            = "heartbeat"
    watchdogPeriod   = 1000
    failureThreshold = 2
    binary           = $false
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8088/register-monitored-subscriber" -Method Post -ContentType "application/json" -Body $body
```

---

### Publish a Single File

Publishes the content of a specific file via a registered publisher.

```shell
$body = @{
    publisherName = "One-shot publisher"
    topic         = "legal"
    file          = "./LICENSE"
    binary        = $false
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8088/publish-files" -Method Post -ContentType "application/json" -Body $body
```

---

### Publish All Files in a Directory as Binary

Publishes all files within a specified directory via a registered publisher, with a delay between each file.

```shell
$body = @{
    publisherName = "One-shot publisher"
    topic         = "archive"
    directory     = ".developer_files/sample-data/binary"
    delay         = 1000
    binary        = $true
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8088/publish-files" -Method Post -ContentType "application/json" -Body $body
```

---

### Deregister a Publisher

Deregisters an existing publisher by its name. This works for both one-shot and periodic publishers. All associated
resources, such as ZMQ sockets and background executors, will be released.

```shell
$body = @{
    name = "periodic publisher 1"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8088/deregister-publisher" -Method Post -ContentType "application/json" -Body $body
```

---

### List All Publishers

Returns a list of all registered publishers (one-shot and periodic) and their details.

```shell
Invoke-RestMethod -Uri "http://localhost:8088/list-publishers" -Method Get
```

---

### Publish All Files in a Directory as Text

Publishes all files within a specified directory via a registered publisher, with a delay between each file.

```shell
$body = @{
    publisherName = "One-shot publisher"
    topic         = "archive"
    directory     = ".developer_files/sample-data/text"
    delay         = 1000
    binary        = $false
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8088/publish-files" -Method Post -ContentType "application/json" -Body $body
```
