-- Comprehensive Lua script for integration test covering all ZmqService features

local pubName = "test-pub"
local periodicPubName = "periodic-test-pub"
local subName = "test-sub"
local monitoredSubName = "monitored-sub"
local topic = "test-topic"
local heartbeatTopic = "heartbeat"
local address = "tcp://*:5562"
local periodicAddress = "tcp://*:5563"
local connectAddress = "tcp://127.0.0.1:5562"
local periodicConnectAddress = "tcp://127.0.0.1:5563"
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

-- 4. Register a periodic publisher
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

-- 8. Update periodic frequency
zmq:updatePeriodicFreq(periodicPubName, 500)
helper:sleep(1500)

-- 9. Register a monitored subscriber
zmq:registerMonitoredSub(monitoredSubName, periodicConnectAddress, topic, 1000, 3, false)
helper:sleep(2000)

-- 10. List publishers
local publishers = zmq:listPublishers()
-- We can iterate over the table if needed, but just calling it verifies it doesn't crash
helper:sleep(500)

-- 11. Publish files (using testFilePath injected by the test)
zmq:publishFiles(pubName, topic, testFilePath, 100, true)
helper:sleep(1000)

-- 12. Publish file list (using testFilePath's directory)
-- We need to extract the directory and filename from testFilePath
-- Since Lua doesn't have a built-in path library, we'll assume the test class can help or we just use the full path if supported
-- Actually, pubFileList expects a directory and a list of names.
-- For the sake of this test, we'll just skip it if we don't have a clean way to split the path,
-- OR we can try to guess it.
local dir = testFilePath:match("(.*)/")
local filename = testFilePath:match(".*/(.*)")
if dir and filename then
    zmq:publishFileList(pubName, topic, dir, {filename}, 100, true)
    helper:sleep(1000)
end

-- 13. Deregister publishers
zmq:deregisterPublisher(pubName)
zmq:deregisterPublisher(periodicPubName)

return "Lua script completed successfully"
