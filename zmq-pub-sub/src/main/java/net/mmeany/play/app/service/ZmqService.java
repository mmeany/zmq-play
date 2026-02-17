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
import java.util.Map;
import java.util.concurrent.*;

@Service
@Slf4j
public class ZmqService {

    private final ZContext zContext = new ZContext(1);
    private final Map<String, ZMQ.Socket> publishers = new ConcurrentHashMap<>();
    private final Map<String, ZMQ.Socket> subscribers = new ConcurrentHashMap<>();
    private final Map<String, ExecutorService> subscriberExecutors = new ConcurrentHashMap<>();
    private final Map<String, ZMQ.Socket> periodicPublishers = new ConcurrentHashMap<>();
    private final Map<String, ScheduledExecutorService> periodicExecutors = new ConcurrentHashMap<>();
    private final Map<String, Object> periodicMessages = new ConcurrentHashMap<>();

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

        for (ScheduledExecutorService executor : periodicExecutors.values()) {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Periodic executor did not terminate in time");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        for (ZMQ.Socket publisher : periodicPublishers.values()) {
            publisher.close();
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

        ZMQ.Socket publisher = publishers.get(publisherName);
        if (publisher == null) {
            throw new IllegalArgumentException("Unknown publisher: " + publisherName);
        }

        try {
            String json = ConfiguredObjectMapper.JSON_MAPPER.writeValueAsString(data);
            synchronized (publisher) {
                publisher.send(topic, ZMQ.SNDMORE);
                publisher.send(json, 0);
            }
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

    /**
     * Registers a new periodic publisher.
     *
     * @param name    The name of the publisher.
     * @param address The address to bind the publisher to.
     * @param topic   The topic to publish on.
     * @param message The message to publish.
     * @param period  The period in milliseconds.
     */
    public void registerPeriodicPublisher(String name, String address, String topic, Object message, long period) {

        log.info("Registering periodic publisher {} on {} with topic {} every {}ms", name, address, topic, period);
        ZMQ.Socket publisher = zContext.createSocket(SocketType.PUB);
        publisher.bind(address);

        periodicPublishers.put(name, publisher);
        periodicMessages.put(name, message);

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        periodicExecutors.put(name, executor);

        executor.scheduleAtFixedRate(() -> {
            try {
                Object currentMessage = periodicMessages.get(name);
                String json = ConfiguredObjectMapper.JSON_MAPPER.writeValueAsString(currentMessage);
                log.debug("Periodically publishing message for {}: {}", name, json);
                publisher.send(topic, ZMQ.SNDMORE);
                publisher.send(json, 0);
            } catch (Exception e) {
                log.error("Error in periodic publisher {}", name, e);
            }
        }, 0, period, TimeUnit.MILLISECONDS);
    }

    /**
     * Updates the message for a periodic publisher.
     *
     * @param name       The name of the publisher.
     * @param newMessage The new message to publish.
     */
    public void updatePeriodicMessage(String name, Object newMessage) {

        if (!periodicPublishers.containsKey(name)) {
            throw new IllegalArgumentException("Unknown periodic publisher: " + name);
        }
        log.info("Updating message for periodic publisher {}", name);
        periodicMessages.put(name, newMessage);
    }
}
