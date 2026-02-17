package net.mmeany.play.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.mmeany.play.app.util.ConfiguredObjectMapper;
import org.springframework.stereotype.Service;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ZmqService {

    private final ZContext zContext = new ZContext(1);
    private final Map<String, ZMQ.Socket> publishers = new HashMap<>();
    private final Map<String, ZMQ.Socket> subscribers = new HashMap<>();
    private final Map<String, ExecutorService> subscriberExecutors = new HashMap<>();

    @PreDestroy
    public void shutdown() {

        for (ZMQ.Socket publisher : publishers.values()) {
            publisher.close();
        }

        for (ExecutorService executor : subscriberExecutors.values()) {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Executor did not terminate in time");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        for (ZMQ.Socket subscriber : subscribers.values()) {
            subscriber.close();
        }

        zContext.close();
    }

    /**
     * Adds a new publisher to the ZMQ service.
     *
     * @param name    The name of the publisher.
     * @param address The address to bind the publisher to.
     */
    public void registerPublisher(String name, String address) {

        log.info("Adding publisher {} on {}", name, address);
        publishers.put(name, zContext.createSocket(SocketType.PUB));
        publishers.get(name).bind(address);
    }

    /**
     * Publishes a message to a publisher.
     *
     * @param publisherName name of registered publisher
     * @param topic         name of topic to publish to
     * @param data          data to publish - will be serialized to JSON
     */
    public void publish(String publisherName, String topic, Object data) {

        if (!publishers.containsKey(publisherName)) {
            throw new IllegalArgumentException("Unknown publisher: " + publisherName);
        }
        ZMQ.Socket publisher = publishers.get(publisherName);
        try {
            //while (!Thread.currentThread().isInterrupted()) {
            String json = ConfiguredObjectMapper.JSON_MAPPER.writeValueAsString(data);
            publisher.send(topic, ZMQ.SNDMORE);
            publisher.send(json, 0);
            //}
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Adds a new subscriber to the ZMQ service.
     *
     * @param name    The name of the subscriber.
     * @param address The address to connect the subscriber to.
     */
    public void registerSubscriber(String name, String address) {

        log.info("Adding subscriber {} on {}", name, address);
        ZMQ.Socket subscriber = zContext.createSocket(SocketType.SUB);
        subscriber.connect(address);
        subscriber.subscribe(""); // Subscribe to all topics

        subscribers.put(name, subscriber);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        subscriberExecutors.put(name, executor);

        executor.submit(() -> {
            log.info("Starting subscriber loop for {}", name);
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    String topic = subscriber.recvStr();
                    if (topic != null) {
                        String message = subscriber.recvStr();
                        if (message != null) {
                            log.debug("Received message on topic: {}", topic);
                            saveToFile(topic, message);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error in subscriber loop for {}", name, e);
            } finally {
                log.info("Subscriber loop for {} finished", name);
            }
        });
    }

    private void saveToFile(String topic, String message) {

        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        String fileName = topic + "-" + timestamp + ".json";
        try {
            Files.writeString(new File(fileName).toPath(), message);
            log.info("Saved message to {}", fileName);
        } catch (IOException e) {
            log.error("Failed to save message to file {}", fileName, e);
        }
    }
}
