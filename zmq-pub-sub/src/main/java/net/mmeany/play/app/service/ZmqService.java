package net.mmeany.play.app.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;

@Service
@Slf4j
public class ZmqService {

    private final String outputDirectory;
    private final ZContext zContext = new ZContext(1);
    private final Map<String, ZMQ.Socket> publishers = new ConcurrentHashMap<>();
    private final Map<String, ZMQ.Socket> subscribers = new ConcurrentHashMap<>();
    private final Map<String, ExecutorService> subscriberExecutors = new ConcurrentHashMap<>();
    private final Map<String, ZMQ.Socket> periodicPublishers = new ConcurrentHashMap<>();
    private final Map<String, ScheduledExecutorService> periodicExecutors = new ConcurrentHashMap<>();
    private final Map<String, String> periodicMessages = new ConcurrentHashMap<>();

    public ZmqService(@Value("${output-directory:output}") String outputDirectory) {

        this.outputDirectory = outputDirectory;
        log.info("Initialized ZmqService with output directory: {}", outputDirectory);
    }

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
    public void publish(String publisherName, String topic, String data) {

        ZMQ.Socket publisher;
        if (publishers.containsKey(publisherName)) {
            publisher = publishers.get(publisherName);
        } else if (periodicPublishers.containsKey(publisherName)) {
            publisher = periodicPublishers.get(publisherName);
        } else {
            throw new IllegalArgumentException("Unknown publisher: " + publisherName);
        }

        synchronized (publisher) {
            publisher.send(topic, ZMQ.SNDMORE);
            publisher.send(data, 0);
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
                            saveToFile(name, topic, message);
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

    private void saveToFile(String subscriberName, String topic, String message) {

        String normalizedSubName = toLowerSnakeCase(subscriberName);
        File subDir = new File(outputDirectory, normalizedSubName);
        if (!subDir.exists() && !subDir.mkdirs()) {
            log.error("Failed to create directory: {}", subDir.getAbsolutePath());
            return;
        }

        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        String fileName = topic + "-" + timestamp + ".json";
        File file = new File(subDir, fileName);
        try {
            Files.writeString(file.toPath(), message);
            log.info("Saved message to {}", file.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to save message to file {}", file.getAbsolutePath(), e);
        }
    }

    private String toLowerSnakeCase(String name) {

        if (name == null) {
            return "null";
        }
        return name.replaceAll("([a-z])([A-Z]+)", "$1_$2")
                   .replaceAll("[^a-zA-Z0-9]+", "_")
                   .toLowerCase();
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
    public void registerPeriodicPublisher(String name, String address, String topic, String message, long period) {

        log.info("Registering periodic publisher {} on {} with topic {} every {}ms", name, address, topic, period);
        ZMQ.Socket publisher = zContext.createSocket(SocketType.PUB);
        publisher.bind(address);

        periodicPublishers.put(name, publisher);
        periodicMessages.put(name, message);

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        periodicExecutors.put(name, executor);

        executor.scheduleAtFixedRate(() -> {
            try {
                String currentMessage = periodicMessages.get(name);
                log.debug("Periodically publishing message for {}: {}", name, currentMessage);
                publisher.send(topic, ZMQ.SNDMORE);
                publisher.send(currentMessage, 0);
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
    public void updatePeriodicMessage(String name, String newMessage) {

        if (!periodicPublishers.containsKey(name)) {
            throw new IllegalArgumentException("Unknown periodic publisher: " + name);
        }
        log.info("Updating message for periodic publisher {}", name);
        periodicMessages.put(name, newMessage);
    }
}
