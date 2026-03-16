package net.mmeany.play.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import net.mmeany.play.app.controller.model.*;
import net.mmeany.play.app.service.ZmqService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

    private final ZmqService zmqService;

    public Controller(ZmqService zmqService) {

        this.zmqService = zmqService;
    }

    @PostMapping("/register-publisher")
    @Operation(summary = "Register a new publisher")
    public ResponseEntity<?> registerPublisher(@RequestBody PublisherRegistrationRequest request) {

        zmqService.registerPublisher(request.getName(), request.getAddress());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/list-publishers")
    @Operation(summary = "List all registered publishers")
    public ResponseEntity<java.util.List<PublisherDetails>> listPublishers() {

        return ResponseEntity.ok(zmqService.listPublishers());
    }

    @PostMapping("/deregister-publisher")
    @Operation(summary = "Deregister an existing publisher")
    public ResponseEntity<?> deregisterPublisher(@RequestBody DeregisterPublisherRequest request) {

        zmqService.deregisterPublisher(request.getName());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/publish")
    @Operation(summary = "Publish a message")
    public ResponseEntity<?> publish(@RequestBody PublishRequest request) {

        byte[] data = request.getMessage() != null ? request.getMessage().getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0];
        zmqService.publish(request.getPublisherName(), request.getTopic(), data);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/register-subscriber")
    @Operation(summary = "Register a new subscriber")
    public ResponseEntity<?> registerSubscriber(@RequestBody SubscriberRegistrationRequest request) {

        zmqService.registerSubscriber(request.getName(), request.getAddress(), request.isBinary());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/register-periodic-publisher")
    @Operation(summary = "Register a new periodic publisher")
    public ResponseEntity<?> registerPeriodicPublisher(@RequestBody PeriodicPublisherRegistrationRequest request) {

        zmqService.registerPeriodicPublisher(request.getName(), request.getAddress(), request.getTopic(), request.getMessage(), request.getPeriod());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/register-monitored-subscriber")
    @Operation(summary = "Register a new monitored subscriber")
    public ResponseEntity<?> registerMonitoredSubscriber(@RequestBody MonitoredSubscriberRegistrationRequest request) {

        zmqService.registerMonitoredSubscriber(request.getName(), request.getAddress(), request.getTopic(), request.getWatchdogPeriod(), request.getFailureThreshold(), request.isBinary());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/update-periodic-message")
    @Operation(summary = "Update the message of a periodic publisher")
    public ResponseEntity<?> updatePeriodicMessage(@RequestBody PeriodicPublisherUpdateRequest request) {

        zmqService.updatePeriodicMessage(request.getName(), request.getMessage());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/publish-files")
    @Operation(summary = "Publish file(s) to a registered publisher on a given topic")
    public ResponseEntity<?> publishFiles(@RequestBody PublishFilesRequest request) {

        if ((request.getDirectory() == null || request.getDirectory().isBlank()) && (request.getFile() == null || request.getFile().isBlank())) {
            return ResponseEntity.badRequest().body("Either 'directory' or 'file' must be provided");
        }
        if (request.getTopic() == null || request.getTopic().isBlank()) {
            return ResponseEntity.badRequest().body("'topic' is required");
        }
        if (request.getPublisherName() == null || request.getPublisherName().isBlank()) {
            return ResponseEntity.badRequest().body("'publisherName' is required");
        }

        try {
            java.util.List<java.io.File> filesToPublish = new java.util.ArrayList<>();
            if (request.getDirectory() != null && !request.getDirectory().isBlank()) {
                java.io.File dir = new java.io.File(request.getDirectory());
                if (!dir.isDirectory()) {
                    return ResponseEntity.badRequest().body("'directory' is not a directory: " + dir.getAbsolutePath());
                }
                java.io.File[] files = dir.listFiles(java.io.File::isFile);
                if (files != null) {
                    java.util.Arrays.sort(files, java.util.Comparator.comparing(java.io.File::getName));
                    filesToPublish.addAll(java.util.Arrays.asList(files));
                }
            } else if (request.getFile() != null && !request.getFile().isBlank()) {
                java.io.File f = new java.io.File(request.getFile());
                if (!f.isFile()) {
                    return ResponseEntity.badRequest().body("'file' is not a file: " + f.getAbsolutePath());
                }
                filesToPublish.add(f);
            }

            long delay = request.getDelay() != null ? request.getDelay() : 0L;
            zmqService.publishFiles(request.getPublisherName(), request.getTopic(), filesToPublish, delay, request.isBinary());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to publish files: " + e.getMessage());
        }
    }

    private static byte[] readFileAsBytes(java.io.File file, boolean binary) throws java.io.IOException {

        if (binary) {
            return java.nio.file.Files.readAllBytes(file.toPath());
        } else {
            String content = java.nio.file.Files.readString(file.toPath());
            return content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
