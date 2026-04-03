package net.mmeany.play.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import net.mmeany.play.app.controller.model.*;
import net.mmeany.play.app.exception.AppException;
import net.mmeany.play.app.service.LuaService;
import net.mmeany.play.app.service.ZmqService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@RestController
@Slf4j
public class Controller {

    private final ZmqService zmqService;
    private final LuaService luaService;

    public Controller(ZmqService zmqService, LuaService luaService) {

        this.zmqService = zmqService;
        this.luaService = luaService;
    }

    @PostMapping("/register-publisher")
    @Operation(summary = "Register a new publisher")
    public ResponseEntity<SuccessResponse> registerPublisher(@Valid @RequestBody PublisherRegistrationRequest request) {

        return ResponseEntity.ok(
                SuccessResponse.builder()
                               .success(zmqService.registerPublisher(request.getName(), request.getAddress()))
                               .build());
    }

    @GetMapping("/list-publishers")
    @Operation(summary = "List all registered publishers")
    public ResponseEntity<List<PublisherDetails>> listPublishers() {

        return ResponseEntity.ok(zmqService.listPublishers());
    }

    @PostMapping("/deregister-publisher")
    @Operation(summary = "Deregister an existing publisher")
    public ResponseEntity<SuccessResponse> deregisterPublisher(@Valid @RequestBody DeregisterPublisherRequest request) {

        return ResponseEntity.ok(
                SuccessResponse.builder()
                               .success(zmqService.deregisterPublisher(request.getName()))
                               .build());
    }

    @PostMapping("/deregister-subscriber")
    @Operation(summary = "Deregister an existing subscriber")
    public ResponseEntity<SuccessResponse> deregisterSubscriber(@Valid @RequestBody DeregisterSubscriberRequest request) {

        return ResponseEntity.ok(
                SuccessResponse.builder()
                               .success(zmqService.deregisterSubscriber(request.getName()))
                               .build());
    }

    @PostMapping("/publish")
    @Operation(summary = "Publish a message if provided, otherwise publish just the topic")
    public ResponseEntity<SuccessResponse> publish(@Valid @RequestBody PublishRequest request) {

        byte[] data = request.getMessage() != null && !request.getMessage().isBlank()
                ? request.getMessage().getBytes(StandardCharsets.UTF_8)
                : new byte[0];
        return ResponseEntity.ok(
                SuccessResponse.builder()
                               .success(zmqService.publish(request.getPublisherName(), request.getTopic(), data))
                               .build());
    }

    @PostMapping("/register-subscriber")
    @Operation(summary = "Register a new subscriber")
    public ResponseEntity<SuccessResponse> registerSubscriber(@Valid @RequestBody SubscriberRegistrationRequest request) {

        return ResponseEntity.ok(
                SuccessResponse.builder()
                               .success(zmqService.registerSubscriber(request.getName(), request.getAddress(), request.isBinary()))
                               .build());
    }

    @PostMapping("/register-periodic-publisher")
    @Operation(summary = "Register a new periodic publisher")
    public ResponseEntity<SuccessResponse> registerPeriodicPublisher(@Valid @RequestBody PeriodicPublisherRegistrationRequest request) {

        return ResponseEntity.ok(
                SuccessResponse.builder()
                               .success(zmqService.registerPeriodicPublisher(request.getName(),
                                                                             request.getAddress(),
                                                                             request.getTopic(),
                                                                             request.getMessage(),
                                                                             request.getPeriod()))
                               .build());
    }

    @PostMapping("/register-monitored-subscriber")
    @Operation(summary = "Register a new monitored subscriber")
    public ResponseEntity<SuccessResponse> registerMonitoredSubscriber(@Valid @RequestBody MonitoredSubscriberRegistrationRequest request) {

        return ResponseEntity.ok(
                SuccessResponse.builder()
                               .success(zmqService.registerMonitoredSubscriber(request.getName(),
                                                                               request.getAddress(),
                                                                               request.getTopic(),
                                                                               request.getWatchdogPeriod(),
                                                                               request.getFailureThreshold(),
                                                                               request.isBinary()))
                               .build());
    }

    @PostMapping("/update-periodic-message")
    @Operation(summary = "Update the message of a periodic publisher")
    public ResponseEntity<SuccessResponse> updatePeriodicMessage(@Valid @RequestBody PeriodicPublisherUpdateRequest request) {

        return ResponseEntity.ok(
                SuccessResponse.builder()
                               .success(zmqService.updatePeriodicMessage(request.getName(), request.getMessage()))
                               .build());
    }

    @PostMapping("/enable-periodic-publisher")
    @Operation(summary = "Enable or disable a periodic publisher")
    public ResponseEntity<SuccessResponse> enablePeriodicPublisher(@Valid @RequestBody PeriodicPublisherStatusRequest request) {

        return ResponseEntity.ok(
                SuccessResponse.builder()
                               .success(zmqService.enablePeriodicPublisher(request.getName(), request.isEnabled()))
                               .build());
    }

    @PostMapping("/update-periodic-frequency")
    @Operation(summary = "Update the frequency of a periodic publisher")
    public ResponseEntity<SuccessResponse> updatePeriodicFrequency(@Valid @RequestBody PeriodicPublisherFrequencyRequest request) {

        return ResponseEntity.ok(
                SuccessResponse.builder()
                               .success(zmqService.updatePeriodicFrequency(request.getName(), request.getPeriod()))
                               .build());
    }

    @PostMapping("/publish-files")
    @Operation(summary = "Publish file(s) to a registered publisher on a given topic")
    public ResponseEntity<SuccessResponse> publishFiles(@Valid @RequestBody PublishFilesRequest request) {

        try {
            List<File> filesToPublish = new ArrayList<>();
            if (request.getDirectory() != null && !request.getDirectory().isBlank()) {
                Path dirPath = zmqService.validatePath(request.getDirectory());
                File dir = dirPath.toFile();
                if (!dir.isDirectory()) {
                    throw new AppException("'directory' is not a directory: " + dir.getAbsolutePath());
                }
                File[] files = dir.listFiles(File::isFile);
                if (files != null) {
                    Arrays.sort(files, Comparator.comparing(File::getName));
                    filesToPublish.addAll(Arrays.asList(files));
                }
            } else if (request.getFile() != null && !request.getFile().isBlank()) {
                Path filePath = zmqService.validatePath(request.getFile());
                File f = filePath.toFile();
                if (!f.isFile()) {
                    throw new AppException("'file' is not a file: " + f.getAbsolutePath());
                }
                filesToPublish.add(f);
            } else {
                throw new AppException("Either 'directory' or 'file' must be provided");
            }

            long delay = request.getDelay() != null ? request.getDelay() : 0L;
            return ResponseEntity.ok(
                    SuccessResponse.builder()
                                   .success(zmqService.publishFiles(request.getPublisherName(),
                                                                    request.getTopic(),
                                                                    filesToPublish,
                                                                    delay,
                                                                    request.isBinary()))
                                   .build());
        } catch (Exception e) {
            log.error("Failed to publish files: {}", e.getMessage(), e);
            throw new AppException("Failed to publish files: " + e.getMessage());
        }
    }

    @PostMapping("/publish-file-list")
    @Operation(summary = "Publish a specific list of files from a directory")
    public ResponseEntity<SuccessResponse> publishFileList(@Valid @RequestBody PublishFileListRequest request) {

        return ResponseEntity.ok(
                SuccessResponse.builder()
                               .success(zmqService.publishFileList(request.getPublisherName(),
                                                                   request.getTopic(),
                                                                   request.getDirectory(),
                                                                   request.getFiles(),
                                                                   request.getDelay(),
                                                                   request.isBinary()))
                               .build());
    }

    @PostMapping("/execute-lua")
    @Operation(summary = "Execute a Lua script")
    public ResponseEntity<LuaExecutionResponse> executeLua(@Valid @RequestBody LuaExecutionRequest request) {

        String scriptToExecute = request.getScript();

        if (request.getFileName() != null && !request.getFileName().isBlank()) {
            Path filePath = zmqService.validatePath(request.getFileName());
            File file = filePath.toFile();
            if (!file.isFile()) {
                return ResponseEntity.badRequest().body(
                        LuaExecutionResponse.builder()
                                            .success(false)
                                            .error("Lua script file not found: " + file.getAbsolutePath())
                                            .build());
            }
            try {
                scriptToExecute = Files.readString(file.toPath());
            } catch (IOException e) {
                return ResponseEntity.internalServerError().body(
                        LuaExecutionResponse.builder()
                                            .success(false)
                                            .error("Failed to read Lua script file: " + e.getMessage())
                                            .build());
            }
        }

        if (scriptToExecute == null || scriptToExecute.isBlank()) {
            return ResponseEntity.badRequest().body(
                    LuaExecutionResponse.builder()
                                        .success(false)
                                        .error("Either 'script' or 'fileName' must be provided")
                                        .build());
        }

        LuaExecutionResponse response = luaService.executeScript(scriptToExecute);
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            log.error("LUA ERROR: {}", response.getError());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
