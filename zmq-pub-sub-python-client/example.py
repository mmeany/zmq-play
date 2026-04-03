import json
import time

from zmq_pub_sub_client import ZmqClient, ZmqApiError


def run_test_scenario():
    # Configuration is automatically picked up from ZMQ_CLIENT_BASE_URL env var
    with ZmqClient() as client:
        try:
            print("Registering periodic heartbeat...")
            client.register_periodic_publisher(
                name="pub1",
                address="tcp://*:6002",
                topic="status",
                message="",
                period=1000
            )

            print("Registering subscriber...")
            client.register_subscriber(
                name="sub1",
                address="tcp://localhost:6002",
                binary=False
            )

            print("Sleep for 5 seconds")
            time.sleep(5)

            print("Publishing test messages...")
            for i in range(10):
                msg = {"message": f"Message {i}"}
                client.publish(publisher_name="pub1", topic="one-shot", message=json.dumps(msg))

            print("Sleep for 5 seconds")
            time.sleep(5)

            print("Executing remote Lua diagnostic...")
            lua_res = client.execute_lua(script="return 'System Ready'")
            print(f"Server Status: {lua_res.result}")

        except ZmqApiError as e:
            print(f"API Error: {e}")


if __name__ == "__main__":
    run_test_scenario()
