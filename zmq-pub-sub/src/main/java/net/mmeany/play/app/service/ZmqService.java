package net.mmeany.play.app.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.mmeany.play.app.controller.model.PublisherDetails;
import net.mmeany.play.app.event.MonitoredSubscriberDownEvent;
import net.mmeany.play.app.exception.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

@Service
@Slf4j
public class ZmqService {

    private final String outputDirectory;
    private final boolean clearOutputDirOnStartup;
    private final ApplicationEventPublisher eventPublisher;
    private final Path workspaceRoot;

    private final ZContext zContext = new ZContext(1);
    private final Map<String, ZMQ.Socket> publishers = new ConcurrentHashMap<>();
    private final Map<String, String> publisherAddresses = new ConcurrentHashMap<>();
    private final Map<String, ZMQ.Socket> subscribers = new ConcurrentHashMap<>();
    private final Map<String, ExecutorService> subscriberExecutors = new ConcurrentHashMap<>();
    private final Map<String, ZMQ.Socket> periodicPublishers = new ConcurrentHashMap<>();
    private final Map<String, String> periodicMessages = new ConcurrentHashMap<>();
    private final Map<String, String> periodicAddresses = new ConcurrentHashMap<>();
    private final Map<String, String> periodicTopics = new ConcurrentHashMap<>();
    private final Map<String, Long> periodicPeriods = new ConcurrentHashMap<>();
    private final Map<String, Boolean> periodicEnabled = new ConcurrentHashMap<>();

    private final ScheduledExecutorService periodicTaskExecutor = Executors.newScheduledThreadPool(4);
    private final Map<String, ScheduledFuture<?>> periodicTasks = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> monitoredTasks = new ConcurrentHashMap<>();

    private final ScheduledExecutorService filePublishExecutor = Executors.newScheduledThreadPool(4);

    public ZmqService(@Value("${output-directory:output}") String outputDirectory,
                      @Value("${output-directory-clear-on-startup:false}") boolean clearOutputDirOnStartup,
                      @Value("${workspace-root:.}") String workspaceRoot,
                      ApplicationEventPublisher eventPublisher) {

        this.outputDirectory = outputDirectory;
        this.clearOutputDirOnStartup = clearOutputDirOnStartup;
        this.workspaceRoot = Paths.get(workspaceRoot).toAbsolutePath().normalize();
        this.eventPublisher = eventPublisher;
        log.info("Initialized ZmqService with output directory: {} (clearOnStartup={}) and workspace root: {}",
                 outputDirectory, clearOutputDirOnStartup, this.workspaceRoot);
    }

    @PostConstruct
    public void init() {

        try {
            Path outPath = Paths.get(outputDirectory);
            if (clearOutputDirOnStartup) {
                log.info("Clearing output directory on startup: {}", outPath.toAbsolutePath());
                if (Files.exists(outPath)) {
                    deleteRecursively(outPath);
                }
            }
            Files.createDirectories(outPath);
        } catch (Exception e) {
            log.error("Failed to initialize output directory {}", outputDirectory, e);
        }
    }

    @PreDestroy
    public void shutdown() {

        periodicTaskExecutor.shutdownNow();
        filePublishExecutor.shutdownNow();

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

        zContext.close();
    }

    private void deleteRecursively(Path path) throws IOException {

        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> pathsStream = Files.walk(path)) {
            pathsStream.sorted(Comparator.reverseOrder())
                       .forEach(p -> {
                           try {
                               Files.deleteIfExists(p);
                           } catch (IOException e) {
                               throw new UncheckedIOException(e);
                           }
                       });
        }
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
     * Validates that a file is within the workspace root.
     *
     * @param path The path to validate.
     * @return The absolute path to the file.
     * @throws AppException If the path is outside the workspace root.
     */
    public Path validatePath(String path) {

        if (path == null || path.isBlank()) {
            throw new AppException("Path cannot be empty");
        }
        Path p = Paths.get(path);
        Path target = p.isAbsolute() ? p.normalize() : workspaceRoot.resolve(p).normalize();

        if (!target.startsWith(workspaceRoot)) {
            log.error("Access denied to path: {} (outside workspace root: {})", target, workspaceRoot);
            throw new AppException("Access denied: path is outside the workspace root");
        }
        return target;
    }

    /**
     * Adds a new publisher to the ZMQ service.
     *
     * @param name    The name of the publisher.
     * @param address The address to bind the publisher to.
     * @return true if the publisher was added successfully, false if it already exists
     */
    public boolean registerPublisher(String name, String address) {

        if (publishers.containsKey(name) || periodicPublishers.containsKey(name)) {
            log.error("Publisher with name '{}' already exists", name);
            return false;
        }
        log.info("Adding publisher {} on {}", name, address);
        ZMQ.Socket publisher = zContext.createSocket(SocketType.PUB);
        publisher.bind(address);
        publishers.put(name, publisher);
        publisherAddresses.put(name, address);
        return true;
    }

    /**
     * Deregisters a publisher from the ZMQ service.
     *
     * @param name The name of the publisher.
     * @return true if the publisher was deregistered successfully, false if it was not found
     */
    public boolean deregisterPublisher(String name) {

        if (publishers.containsKey(name)) {
            log.info("Deregistering publisher {}", name);
            ZMQ.Socket publisher = publishers.remove(name);
            publisherAddresses.remove(name);
            if (publisher != null) {
                publisher.close();
            }
        } else if (periodicPublishers.containsKey(name)) {
            log.info("Deregistering periodic publisher {}", name);
            ZMQ.Socket publisher = periodicPublishers.remove(name);
            if (publisher != null) {
                publisher.close();
            }
            ScheduledFuture<?> task = periodicTasks.remove(name);
            if (task != null) {
                task.cancel(true);
            }
            periodicMessages.remove(name);
            periodicAddresses.remove(name);
            periodicTopics.remove(name);
            periodicPeriods.remove(name);
            periodicEnabled.remove(name);
        } else {
            log.warn("Attempted to deregister unknown publisher: {}", name);
            return false;
        }
        return true;
    }

    /**
     * Publishes a message to a publisher.
     *
     * @param publisherName name of registered publisher
     * @param topic         name of topic to publish to
     * @param data          data to publish
     * @return true if publish was successful, false otherwise
     */
    public boolean publish(String publisherName, String topic, byte[] data) {

        ZMQ.Socket publisher;
        if (publishers.containsKey(publisherName)) {
            publisher = publishers.get(publisherName);
        } else if (periodicPublishers.containsKey(publisherName)) {
            publisher = periodicPublishers.get(publisherName);
        } else {
            log.warn("Unknown publisher: {}", publisherName);
            return false;
        }

        synchronized (publisher) {
            if (data == null || data.length == 0) {
                publisher.send(topic, 0);
            } else {
                publisher.send(topic, ZMQ.SNDMORE);
                publisher.send(data, 0);
            }
        }
        return true;
    }

    /**
     * Adds a new subscriber to the ZMQ service.
     *
     * @param subscriberName The name of the subscriber.
     * @param address        The address to connect the subscriber to.
     * @return true if subscriber registration was successful, false otherwise
     */
    public boolean registerSubscriber(String subscriberName, String address, boolean binary) {

        log.info("Adding subscriber '{}' on '{}' (binary={})", subscriberName, address, binary);

        if (subscribers.containsKey(subscriberName)) {
            log.error("Subscriber with name '{}' already exists", subscriberName);
            return false;
        }

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
                    if (topic == null) continue;
                    log.info("Received a {} message on subscriber {}", binary ? "BINARY" : "TEXT", subscriberName);
                    byte[] messageBytes = getMessageBytes(subscriberName, topic, subscriber);
                    if (messageBytes != null && messageBytes.length > 0) {
                        saveAndMonitor(subscriberName, topic, messageBytes);
                    }
                }
            } catch (Exception e) {
                log.error("Error in subscriber loop for {}", subscriberName, e);
            } finally {
                log.info("Subscriber loop for {} finished", subscriberName);
            }
        });
        return true;
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
     * @return True if the periodic publisher was successfully registered, false otherwise.
     */
    public boolean registerPeriodicPublisher(String name, String address, String topic, String message, long period) {

        log.info("Registering periodic publisher {} on {} with topic {} every {}ms", name, address, topic, period);
        if (publishers.containsKey(name) || periodicPublishers.containsKey(name)) {
            log.error("Publisher with name '{}' already exists", name);
            return false;
        }
        ZMQ.Socket publisher = zContext.createSocket(SocketType.PUB);
        publisher.bind(address);

        periodicPublishers.put(name, publisher);
        periodicMessages.put(name, message);
        periodicAddresses.put(name, address);
        periodicTopics.put(name, topic);
        periodicPeriods.put(name, period);
        periodicEnabled.put(name, true);

        ScheduledFuture<?> task = periodicTaskExecutor.scheduleAtFixedRate(() -> {
            try {
                if (Boolean.FALSE.equals(periodicEnabled.getOrDefault(name, false))) {
                    return;
                }
                String currentMessage = periodicMessages.get(name);
                log.debug("Periodically publishing message for {}: {}", name, currentMessage);
                if (currentMessage != null && !currentMessage.isEmpty()) {
                    synchronized (publisher) {
                        publisher.send(topic, ZMQ.SNDMORE);
                        publisher.send(currentMessage, 0);
                    }
                } else {
                    synchronized (publisher) {
                        publisher.send(topic, 0);
                    }
                }
            } catch (Exception e) {
                log.error("Error in periodic publisher {}", name, e);
            }
        }, 0, period, TimeUnit.MILLISECONDS);

        periodicTasks.put(name, task);
        return true;
    }

    /**
     * Registers a new monitored subscriber.
     *
     * @param name             The name of the subscriber.
     * @param address          The address to connect to.
     * @param topic            The topic to monitor.
     * @param watchdogPeriod   The watchdog period in milliseconds.
     * @param failureThreshold The failure threshold.
     * @return true if registration was successful, false otherwise
     */
    public boolean registerMonitoredSubscriber(String name, String address, String topic, long watchdogPeriod, int failureThreshold, boolean binary) {

        log.info("Registering monitored subscriber {} on {} for topic {} with watchdog {}ms and threshold {} (binary={})",
                 name, address, topic, watchdogPeriod, failureThreshold, binary);

        if (!registerSubscriber(name, address, binary)) {
            return false;
        }

        AtomicLong lastMessageTime = new AtomicLong(System.currentTimeMillis());
        AtomicInteger missedMessages = new AtomicInteger(0);
        ScheduledFuture<?> watchdogTask = periodicTaskExecutor.scheduleAtFixedRate(() -> {
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

        monitoredTasks.put(name, watchdogTask);

        // Store monitoring info to be used by the subscriber loop
        monitoredSubscribers.put(name, new MonitoringInfo(topic, lastMessageTime, missedMessages));
        return true;
    }

    private final Map<String, MonitoringInfo> monitoredSubscribers = new ConcurrentHashMap<>();
    private final Map<String, Boolean> subscriberBinaryFlags = new ConcurrentHashMap<>();

    private record MonitoringInfo(String topic, AtomicLong lastMessageTime, AtomicInteger missedMessages) {}

    /**
     * Updates the message for a periodic publisher.
     *
     * @param name       The name of the publisher.
     * @param newMessage The new message to publish.
     * @return True if the update was successful, false otherwise.
     */
    public boolean updatePeriodicMessage(String name, String newMessage) {

        log.info("Updating message for periodic publisher {}", name);
        if (!periodicPublishers.containsKey(name)) {
            log.error("Unknown periodic publisher: {}", name);
            return false;
        }
        periodicMessages.put(name, newMessage);
        return true;
    }

    /**
     * Enables or disables a periodic publisher.
     *
     * @param name    The name of the publisher.
     * @param enabled Whether to enable or disable the publisher.
     * @return True if the operation was successful, false otherwise.
     */
    public boolean enablePeriodicPublisher(String name, boolean enabled) {

        log.info("{} periodic publisher {}", enabled ? "Enabling" : "Disabling", name);
        if (!periodicPublishers.containsKey(name)) {
            log.error("Unknown periodic publisher: {}", name);
            return false;
        }
        periodicEnabled.put(name, enabled);
        return true;
    }

    /**
     * Updates the frequency of a periodic publisher.
     *
     * @param name   The name of the publisher.
     * @param period The new period in milliseconds.
     * @return True if the operation was successful, false otherwise.
     */
    public boolean updatePeriodicFrequency(String name, long period) {

        log.info("Updating frequency for periodic publisher {} to {}ms", name, period);
        if (!periodicPublishers.containsKey(name)) {
            log.error("Unknown periodic publisher: {}", name);
            return false;
        }

        // To update frequency, we need to restart the task
        ScheduledFuture<?> oldTask = periodicTasks.remove(name);
        if (oldTask != null) {
            oldTask.cancel(true);
        }

        periodicPeriods.put(name, period);
        ZMQ.Socket publisher = periodicPublishers.get(name);
        String topic = periodicTopics.get(name);

        ScheduledFuture<?> newTask = periodicTaskExecutor.scheduleAtFixedRate(() -> {
            try {
                if (Boolean.FALSE.equals(periodicEnabled.getOrDefault(name, false))) {
                    return;
                }
                String currentMessage = periodicMessages.get(name);
                log.debug("Periodically publishing message for {}: {}", name, currentMessage);
                if (currentMessage != null && !currentMessage.isEmpty()) {
                    synchronized (publisher) {
                        publisher.send(topic, ZMQ.SNDMORE);
                        publisher.send(currentMessage, 0);
                    }
                } else {
                    synchronized (publisher) {
                        publisher.send(topic, 0);
                    }
                }
            } catch (Exception e) {
                log.error("Error in periodic publisher {}", name, e);
            }
        }, period, period, TimeUnit.MILLISECONDS);

        periodicTasks.put(name, newTask);
        return true;
    }

    /**
     * Lists all registered publishers.
     *
     * @return A list of publisher details.
     */
    public List<PublisherDetails> listPublishers() {

        List<PublisherDetails> details = new ArrayList<>();

        publishers.forEach((name, socket) ->
                                   details.add(PublisherDetails.builder()
                                                               .name(name)
                                                               .type("one-shot")
                                                               .address(publisherAddresses.get(name))
                                                               .build())
                          );

        periodicPublishers.forEach((name, socket) ->
                                           details.add(PublisherDetails.builder()
                                                                       .name(name)
                                                                       .type("periodic")
                                                                       .address(periodicAddresses.get(name))
                                                                       .topic(periodicTopics.get(name))
                                                                       .message(periodicMessages.get(name))
                                                                       .period(periodicPeriods.get(name))
                                                                       .enabled(periodicEnabled.get(name))
                                                                       .build())
                                  );

        return details;
    }

    /**
     * Publishes a list of files to an existing publisher.
     */
    public boolean publishFiles(String publisherName, String topic, List<File> files, long delayMs, boolean binary) {

        log.info("Scheduling publication of {} file(s) via publisher '{}' on topic '{}' with delay {}ms (binary={})",
                 files.size(), publisherName, topic, delayMs, binary);

        if (files == null || files.isEmpty()) {
            log.warn("No files provided for publishFiles");
            return false;
        }

        if (!publishers.containsKey(publisherName) && !periodicPublishers.containsKey(publisherName)) {
            log.error("Unknown publisher: {}", publisherName);
            return false;
        }

        // Use a single task to publish files with delays to avoid flooding the executor
        filePublishExecutor.submit(() -> {
            for (int i = 0; i < files.size(); i++) {
                File f = files.get(i);
                if (i > 0 && delayMs > 0) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("File publication interrupted");
                        break;
                    }
                }
                try {
                    byte[] data;
                    if (binary) {
                        data = Files.readAllBytes(f.toPath());
                    } else {
                        data = Files.readString(f.toPath()).getBytes(StandardCharsets.UTF_8);
                    }
                    publish(publisherName, topic, data);
                    log.info("Published file: {}", f.getName());
                } catch (IOException e) {
                    log.error("Failed to read file {} for publishing", f.getAbsolutePath(), e);
                }
            }
        });

        return true;
    }

    /**
     * Publishes a list of specific files from a directory.
     *
     * @param publisherName The name of the publisher.
     * @param topic         The topic to publish on.
     * @param directory     The directory containing the files.
     * @param fileNames     The list of file names to publish, in order.
     * @param delayMs       The delay between publications.
     * @param binary        Whether to publish as binary.
     * @return True if all files were published successfully, false otherwise.
     */
    public boolean publishFileList(String publisherName, String topic, String directory, List<String> fileNames, long delayMs, boolean binary) {

        if (fileNames == null || fileNames.isEmpty()) {
            log.warn("No file names provided for publishFileList");
            return false;
        }

        Path dirPath = validatePath(directory);
        File dir = dirPath.toFile();
        if (!dir.isDirectory()) {
            log.error("Not a directory: {}", directory);
            return false;
        }

        List<File> files = new ArrayList<>();
        for (String fileName : fileNames) {
            File f = new File(dir, fileName);
            if (!f.exists() || !f.isFile()) {
                throw new IllegalArgumentException("File not found or not a file: " + f.getAbsolutePath());
            }
            // Ensure individual files are also within workspace root (redundant but safe)
            validatePath(f.getAbsolutePath());
            files.add(f);
        }

        publishFiles(publisherName, topic, files, delayMs, binary);
        return true;
    }
}
