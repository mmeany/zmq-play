package net.mmeany.play.app.util;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicInteger;

public class TestPortUtils {

    private static final AtomicInteger portOffset = new AtomicInteger(0);
    private static final int BASE_PORT = 10000;

    /**
     * Gets a free port for ZMQ to bind to.
     * Note: This is a best effort to find a free port.
     * Since ZMQ binds asynchronously sometimes, we try to use a range.
     */
    public static int getNextAvailablePort() {

        int port = -1;
        while (port == -1) {
            int candidate = BASE_PORT + portOffset.getAndIncrement() % 10000;
            try (ServerSocket socket = new ServerSocket(candidate)) {
                port = candidate;
            } catch (IOException e) {
                // Port already in use, try next one
            }
        }
        return port;
    }

    public static String getBindAddress(int port) {

        return "tcp://*:" + port;
    }

    public static String getConnectAddress(int port) {

        return "tcp://127.0.0.1:" + port;
    }
}
