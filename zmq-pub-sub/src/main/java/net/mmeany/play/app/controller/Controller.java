package net.mmeany.play.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import net.mmeany.play.app.controller.model.*;
import net.mmeany.play.app.service.ZmqService;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/publish")
    @Operation(summary = "Publish a message")
    public ResponseEntity<?> publish(@RequestBody PublishRequest request) {

        zmqService.publish(request.getPublisherName(), request.getTopic(), request.getMessage());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/register-subscriber")
    @Operation(summary = "Register a new subscriber")
    public ResponseEntity<?> registerSubscriber(@RequestBody SubscriberRegistrationRequest request) {

        zmqService.registerSubscriber(request.getName(), request.getAddress());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/register-periodic-publisher")
    @Operation(summary = "Register a new periodic publisher")
    public ResponseEntity<?> registerPeriodicPublisher(@RequestBody PeriodicPublisherRegistrationRequest request) {

        zmqService.registerPeriodicPublisher(request.getName(), request.getAddress(), request.getTopic(), request.getMessage(), request.getPeriod());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/update-periodic-message")
    @Operation(summary = "Update the message of a periodic publisher")
    public ResponseEntity<?> updatePeriodicMessage(@RequestBody PeriodicPublisherUpdateRequest request) {

        zmqService.updatePeriodicMessage(request.getName(), request.getMessage());
        return ResponseEntity.ok().build();
    }
}
