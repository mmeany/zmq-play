### ZMQ Play Utility Lua Integration

The ZMQ Play Utility includes a built-in Lua engine (Luaj) that allows you to automate complex ZeroMQ operations using
scripts. This is particularly useful for orchestration, integration testing, and simulating multi-step messaging
scenarios.

The application exposes a `POST /execute-lua` endpoint that accepts a script in the request body.

---

### Execution Endpoint

**URL**: `http://localhost:8088/execute-lua`  
**Method**: `POST`  
**Body**:

```json
{
  "script": "string (optional)",
  "fileName": "string (optional)"
}
```

*Note: Either `script` or `fileName` must be provided. If `fileName` is supplied, the application loads the script from
the specified file path on the server.*

---

### Exposed Objects

Two global objects are injected into the Lua context:

#### 1. `zmq` Object

The `zmq` object provides access to the core `ZmqService` functionalities.

| Method                 | Parameters                                          | Description                                                          |
|:-----------------------|:----------------------------------------------------|:---------------------------------------------------------------------|
| `registerPublisher`    | `name, address`                                     | Registers a new one-shot publisher.                                  |
| `deregisterPublisher`  | `name`                                              | Stops and removes a registered publisher.                            |
| `deregisterSubscriber` | `name`                                              | Stops and removes a registered subscriber.                           |
| `publish`              | `publisherName, topic, message`                     | Sends a message via a registered publisher.                          |
| `registerSubscriber`   | `name, address, binary`                             | Registers a new subscriber (logs to disk).                           |
| `registerPeriodicPub`  | `name, address, topic, message, period`             | Registers a publisher that sends messages at intervals.              |
| `registerMonitoredSub` | `name, address, topic, watchdog, threshold, binary` | Registers a monitored subscriber with a watchdog.                    |
| `updatePeriodicMsg`    | `name, newMessage`                                  | Updates the message content for a periodic publisher.                |
| `enablePeriodicPub`    | `name, enabled`                                     | Enables (`true`) or disables (`false`) a periodic publisher.         |
| `updatePeriodicFreq`   | `name, period`                                      | Changes the interval (ms) for a periodic publisher.                  |
| `listPublishers`       | (none)                                              | Returns a Lua table of all registered publishers.                    |
| `publishFiles`         | `pubName, topic, filePaths, delay, binary`          | Publishes one or more files. `filePaths` can be a string or a table. |
| `pubFiles`             | (alias)                                             | Alias for `publishFiles`.                                            |
| `publishFileList`      | `pubName, topic, dir, names, delay, binary`         | Publishes a specific list of files from a directory.                 |
| `pubFileList`          | (alias)                                             | Alias for `publishFileList`.                                         |

#### 2. `helper` Object

The `helper` object provides utility functions.

| Method  | Parameters     | Description                     |
|:--------|:---------------|:--------------------------------|
| `sleep` | `milliseconds` | Pauses execution of the script. |

---

### Comprehensive Example

The following script exercises most of the available features. It sets up publishers and subscribers, sends messages,
modifies periodic behavior, and cleans up.

```lua
-- Comprehensive Lua script example

local pubName = "test-pub"
local periodicPubName = "periodic-test-pub"
local subName = "test-sub"
local topic = "test-topic"
local address = "tcp://*:5562"
local periodicAddress = "tcp://*:5563"
local connectAddress = "tcp://127.0.0.1:5562"
local msg = "hello world"

-- 1. Register a one-shot publisher
zmq:registerPublisher(pubName, address)
helper:sleep(500)

-- 2. Register a subscriber
zmq:registerSubscriber(subName, connectAddress, false)
helper:sleep(500)

-- 3. Publish a one-shot message
zmq:publish(pubName, topic, msg)
helper:sleep(1000)

-- 4. Register a periodic publisher (every 1 second)
zmq:registerPeriodicPub(periodicPubName, periodicAddress, topic, "periodic message", 1000)
helper:sleep(1500)

-- 5. Update periodic message
zmq:updatePeriodicMsg(periodicPubName, "updated periodic message")
helper:sleep(1500)

-- 6. Disable periodic publisher
zmq:enablePeriodicPub(periodicPubName, false)
helper:sleep(1500)

-- 7. Enable periodic publisher
zmq:enablePeriodicPub(periodicPubName, true)
helper:sleep(1500)

-- 8. Update periodic frequency (to 500ms)
zmq:updatePeriodicFreq(periodicPubName, 500)
helper:sleep(1500)

-- 9. List publishers and print to console (server-side)
local publishers = zmq:listPublishers()
-- You can iterate over the table if needed

-- 10. Deregister publishers to clean up resources
zmq:deregisterPublisher(pubName)
zmq:deregisterPublisher(periodicPubName)
zmq:deregisterSubscriber(subName)

return "Lua script completed successfully"
```

To execute this via `curl`:

```bash
curl -X POST http://localhost:8088/execute-lua \
     -H "Content-Type: application/json" \
     -d '{
           "script": "zmq:registerPublisher('\''lua-pub'\'', '\''tcp://*:5555'\''); zmq:publish('\''lua-pub'\'', '\''lua-topic'\'', '\''Hello!'\''); return '\''Done'\''"
         }'
```
