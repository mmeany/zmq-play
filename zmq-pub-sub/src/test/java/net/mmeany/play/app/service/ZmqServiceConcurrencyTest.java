package net.mmeany.play.app.service;

import net.mmeany.play.app.util.TestPortUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ZmqServiceConcurrencyTest {

    @TempDir
    Path tempDir;

    @Test
    void testConcurrentPublish() throws Exception {

        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        ZmqService zmqService = new ZmqService(tempDir.toAbsolutePath().toString(), false, tempDir.toAbsolutePath().toString(), eventPublisher);
        String pubName = "concurrentPub";
        int port = TestPortUtils.getNextAvailablePort();
        String pubAddress = TestPortUtils.getBindAddress(port);
        String subAddress = TestPortUtils.getConnectAddress(port);
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
        // We MUST wait for ZMQ to establish connection before publishing,
        // otherwise first messages are lost.
        Thread.sleep(1000);

        int numThreads = 10;
        int messagesPerThread = 50;
        int totalMessages = numThreads * messagesPerThread;
        ExecutorService pubExecutor = Executors.newFixedThreadPool(numThreads);

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            pubExecutor.submit(() -> {
                for (int j = 0; j < messagesPerThread; j++) {
                    String message = "{\"threadId\":%d,\"msgId\":%d}".formatted(threadId, j);
                    zmqService.publish(pubName, topic, message.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            });
        }

        pubExecutor.shutdown();
        pubExecutor.awaitTermination(10, TimeUnit.SECONDS);

        // Wait for all messages to be received
        await().atMost(Duration.ofSeconds(10)).until(() -> {
            synchronized (receivedMessages) {
                return receivedMessages.size() >= totalMessages;
            }
        });

        subExecutor.shutdownNow();
        subscriber.close();
        context.close();
        zmqService.shutdown();

        // Check results
        assertEquals(numThreads * messagesPerThread, receivedMessages.size(),
                     "Should have received all messages. If lower, some multipart messages might have been corrupted.");
    }
}
