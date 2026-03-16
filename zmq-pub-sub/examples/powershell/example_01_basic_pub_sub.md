### ZMQ Play Utility Commands (PowerShell)

This document provides examples of how to interact with the ZMQ Play Utility endpoints using PowerShell's
`Invoke-RestMethod`. From IntelliJ these scripts can be executed directly within the IDE, providing PowerShell is the
default terminal, from this file!

The application is assumed to be running at `http://localhost:8088`.

---

### Register a new One-Shot Publisher

This command registers a new ZeroMQ publisher bound to a specific TCP address.

```shell
$$body = @{
    name    = "One-shot publisher";
    address = "tcp://*:5557";
} | ConvertTo-Json;

Invoke-RestMethod -Uri "http://localhost:8088/register-publisher" -Method Post -ContentType "application/json" -Body $body
```

---

### Register a new Subscriber to address of one shot publisher

This command registers a subscriber that connects to a publisher's address. It will automatically save received messages
to the configured output directory.

```shell
$$body = @{
    name    = "Subscriber 1";
    address = "tcp://localhost:5557";
    binary  = $false;
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

### Publish a Single File as `text`

Publishes the content of a specific file via a registered publisher.

```shell
$$body = @{
    publisherName = "One-shot publisher";
    topic         = "system";
    file          = "./LICENSE";
    binary        = $false;
} | ConvertTo-Json;

Invoke-RestMethod -Uri "http://localhost:8088/publish-files" -Method Post -ContentType "application/json" -Body $body
```

---

### Publish All Files in a Directory as text

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
