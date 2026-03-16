package net.mmeany.play.app.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.mmeany.play.app.event.MonitoredSubscriberDownEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class ZmqService {

    private final String outputDirectory;
    private final boolean clearOutputDirOnStartup;
    private final ApplicationEventPublisher eventPublisher;
    private final ZContext zContext = new ZContext(1);
    private final Map<String, ZMQ.Socket> publishers = new ConcurrentHashMap<>();
    private final Map<String, ZMQ.Socket> subscribers = new ConcurrentHashMap<>();
    private final Map<String, ExecutorService> subscriberExecutors = new ConcurrentHashMap<>();
    private final Map<String, ZMQ.Socket> periodicPublishers = new ConcurrentHashMap<>();
    private final Map<String, ScheduledExecutorService> periodicExecutors = new ConcurrentHashMap<>();
    private final Map<String, String> periodicMessages = new ConcurrentHashMap<>();
    private final Map<String, ScheduledExecutorService> monitoredExecutors = new ConcurrentHashMap<>();
    private final ScheduledExecutorService filePublishExecutor = Executors.newScheduledThreadPool(4);

    public ZmqService(@Value("${output-directory:output}") String outputDirectory,
                      @Value("${output-directory-clear-on-startup:false}") boolean clearOutputDirOnStartup,
                      ApplicationEventPublisher eventPublisher) {

        this.outputDirectory = outputDirectory;
        this.clearOutputDirOnStartup = clearOutputDirOnStartup;
        this.eventPublisher = eventPublisher;
        log.info("Initialized ZmqService with output directory: {} (clearOnStartup={})", outputDirectory, clearOutputDirOnStartup);
    }

    @jakarta.annotation.PostConstruct
    public void init() {

        try {
            java.nio.file.Path outPath = java.nio.file.Paths.get(outputDirectory);
            if (clearOutputDirOnStartup) {
                log.info("Clearing output directory on startup: {}", outPath.toAbsolutePath());
                if (java.nio.file.Files.exists(outPath)) {
                    deleteRecursively(outPath);
                }
            }
            java.nio.file.Files.createDirectories(outPath);
        } catch (Exception e) {
            log.error("Failed to initialize output directory {}", outputDirectory, e);
        }
    }

    @PreDestroy
    public void shutdown() {

        for (ZMQ.Socket publisher : publishers.values()) {
            publisher.close();
        }

        for (ZMQ.Socket subscriber : subscribers.values()) {
            subscriber.close();
        }

        for (ZMQ.Socket publisher : periodicPublishers.values()) {
            publisher.close();
        }

        for (ExecutorService executor : subscriberExecutors.values()) {
            shutdownExecutorService("Subscriber executor", executor);
        }

        for (ScheduledExecutorService executor : periodicExecutors.values()) {
            shutdownExecutorService("Periodic executor", executor);
        }

        for (ScheduledExecutorService executor : monitoredExecutors.values()) {
            shutdownExecutorService("Monitored executor", executor);
        }

        shutdownExecutorService("File publish executor", filePublishExecutor);

        zContext.close();
    }

    private void deleteRecursively(java.nio.file.Path path) throws java.io.IOException {

        if (!java.nio.file.Files.exists(path)) return;
        java.nio.file.Files.walk(path)
                           .sorted(java.util.Comparator.reverseOrder())
                           .forEach(p -> {
                               try {
                                   java.nio.file.Files.deleteIfExists(p);
                               } catch (java.io.IOException e) {
                                   throw new RuntimeException(e);
                               }
                           });
    }

    private void shutdownExecutorService(String executorName, ExecutorService executor) {

        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("{} did not terminate in time", executorName);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
    public void publish(String publisherName, String topic, byte[] data) {

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
     * @param subscriberName The name of the subscriber.
     * @param address        The address to connect the subscriber to.
     */
    public void registerSubscriber(String subscriberName, String address, boolean binary) {

        log.info("Adding subscriber '{}' on '{}' (binary={})", subscriberName, address, binary);
        ZMQ.Socket subscriber = zContext.createSocket(SocketType.SUB);
        subscriber.connect(address);
        subscriber.subscribe(""); // Subscribe to all topics

        subscribers.put(subscriberName, subscriber);
        subscriberBinaryFlags.put(subscriberName, binary);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        subscriberExecutors.put(subscriberName, executor);

        executor.submit(() -> {
            log.info("Starting subscriber loop for {}", subscriberName);
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    String topic = subscriber.recvStr();
                    log.info("Received a {} message on subscriber {}", binary ? "BINARY" : "TEXT", subscriberName);
                    byte[] messageBytes = getMessageBytes(subscriberName, topic, subscriber);
                    if (messageBytes.length > 0) {
                        saveAndMonitor(subscriberName, topic, messageBytes);
                    }
                }
            } catch (Exception e) {
                log.error("Error in subscriber loop for {}", subscriberName, e);
            } finally {
                log.info("Subscriber loop for {} finished", subscriberName);
            }
        });
    }

    private byte[] getMessageBytes(String subscriberName, String topic, ZMQ.Socket subscriber) {

        if (topic != null) {
            boolean isBinary = subscriberBinaryFlags.getOrDefault(subscriberName, false);
            if (isBinary) {
                return subscriber.recv(0);
            } else {
                String message = subscriber.recvStr();
                if (message != null) {
                    return message.getBytes(StandardCharsets.UTF_8);
                }
            }
        }
        return new byte[0];
    }

    private void saveAndMonitor(String name, String topic, byte[] messageBytes) {

        log.info("Saving message received by '{}' to file and resetting watchdog.", topic);
        saveToFile(name, topic, messageBytes);
        MonitoringInfo monitor = monitoredSubscribers.get(name);
        if (monitor != null && monitor.topic().equals(topic)) {
            monitor.lastMessageTime().set(System.currentTimeMillis());
            monitor.missedMessages().set(0);
        }
    }

    private void saveToFile(String subscriberName, String topic, byte[] message) {

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
            Files.write(file.toPath(), message);
            log.info("Saved binary message to {}", file.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to save binary message to file {}", file.getAbsolutePath(), e);
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
     * Registers a new monitored subscriber.
     *
     * @param name             The name of the subscriber.
     * @param address          The address to connect to.
     * @param topic            The topic to monitor.
     * @param watchdogPeriod   The watchdog period in milliseconds.
     * @param failureThreshold The failure threshold.
     */
    public void registerMonitoredSubscriber(String name, String address, String topic, long watchdogPeriod, int failureThreshold, boolean binary) {

        log.info("Registering monitored subscriber {} on {} for topic {} with watchdog {}ms and threshold {} (binary={})",
                 name, address, topic, watchdogPeriod, failureThreshold, binary);

        registerSubscriber(name, address, binary);

        AtomicLong lastMessageTime = new AtomicLong(System.currentTimeMillis());
        AtomicInteger missedMessages = new AtomicInteger(0);
        ScheduledExecutorService watchdogExecutor = Executors.newSingleThreadScheduledExecutor();
        monitoredExecutors.put(name, watchdogExecutor);

        watchdogExecutor.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            if (now - lastMessageTime.get() > watchdogPeriod) {
                int missed = missedMessages.incrementAndGet();
                log.warn("Subscriber {} missed heartbeat on topic {}. Total missed: {}", name, topic, missed);
                if (missed >= failureThreshold) {
                    log.error("Subscriber {} is DOWN (threshold {} exceeded)", name, failureThreshold);
                    eventPublisher.publishEvent(new MonitoredSubscriberDownEvent(this, name, topic));
                }
                // Reset lastMessageTime so we don't immediately trigger again in the next period unless another period passes
                lastMessageTime.set(now);
            }
        }, watchdogPeriod, watchdogPeriod, TimeUnit.MILLISECONDS);

        // Store monitoring info to be used by the subscriber loop
        monitoredSubscribers.put(name, new MonitoringInfo(topic, lastMessageTime, missedMessages));
    }

    private final Map<String, MonitoringInfo> monitoredSubscribers = new ConcurrentHashMap<>();
    private final Map<String, Boolean> subscriberBinaryFlags = new ConcurrentHashMap<>();

    private record MonitoringInfo(String topic, AtomicLong lastMessageTime, AtomicInteger missedMessages) {}

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

    /**
     * Publishes a list of files to an existing publisher.
     */
    public void publishFiles(String publisherName, String topic, java.util.List<File> files, long delayMs, boolean binary) {

        if (files == null || files.isEmpty()) {
            log.warn("No files provided for publishFiles");
            return;
        }

        log.info("Scheduling publication of {} file(s) via publisher '{}' on topic '{}' with delay {}ms (binary={})",
                 files.size(), publisherName, topic, delayMs, binary);

        for (int i = 0; i < files.size(); i++) {
            final File f = files.get(i);
            long delay = i * delayMs;
            filePublishExecutor.schedule(() -> {
                try {
                    byte[] data;
                    if (binary) {
                        data = Files.readAllBytes(f.toPath());
                    } else {
                        data = Files.readString(f.toPath()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    }
                    publish(publisherName, topic, data);
                    log.info("Published file: {}", f.getName());
                } catch (IOException e) {
                    log.error("Failed to read file {} for publishing", f.getAbsolutePath(), e);
                }
            }, delay, TimeUnit.MILLISECONDS);
        }
    }
}
