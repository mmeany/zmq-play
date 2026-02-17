package net.mmeany.play.app.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.mmeany.play.app.util.ConfiguredObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZmqServiceConcurrencyTest {

    @TempDir
    Path tempDir;

    @Test
    void testConcurrentPublish() throws Exception {

        ZmqService zmqService = new ZmqService(tempDir.toAbsolutePath().toString());
        String pubName = "concurrentPub";
        String pubAddress = "tcp://*:5558";
        String subAddress = "tcp://127.0.0.1:5558";
        String topic = "concurrent-topic";

        zmqService.registerPublisher(pubName, pubAddress);

        // Setup a subscriber to collect messages
        List<String> receivedMessages = new ArrayList<>();
        ZContext context = new ZContext();
        ZMQ.Socket subscriber = context.createSocket(SocketType.SUB);
        subscriber.connect(subAddress);
        subscriber.subscribe(topic.getBytes(ZMQ.CHARSET));

        ExecutorService subExecutor = Executors.newSingleThreadExecutor();
        subExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                String t = subscriber.recvStr();
                if (t == null) break;
                String m = subscriber.recvStr();
                if (m == null) break;
                synchronized (receivedMessages) {
                    receivedMessages.add(m);
                }
            }
        });

        // Wait for connection
        Thread.sleep(1000);

        int numThreads = 10;
        int messagesPerThread = 50;
        ExecutorService pubExecutor = Executors.newFixedThreadPool(numThreads);

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            pubExecutor.submit(() -> {
                for (int j = 0; j < messagesPerThread; j++) {
                    ObjectNode node = ConfiguredObjectMapper.JSON_MAPPER.createObjectNode();
                    node.put("threadId", threadId);
                    node.put("msgId", j);
                    zmqService.publish(pubName, topic, node);
                }
            });
        }

        pubExecutor.shutdown();
        pubExecutor.awaitTermination(10, TimeUnit.SECONDS);

        // Wait for all messages to be received
        Thread.sleep(2000);

        subExecutor.shutdownNow();
        subscriber.close();
        context.close();
        zmqService.shutdown();

        // Check results
        assertEquals(numThreads * messagesPerThread, receivedMessages.size(),
                     "Should have received all messages. If lower, some multipart messages might have been corrupted.");
    }
}
