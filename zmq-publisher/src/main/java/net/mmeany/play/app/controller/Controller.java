package net.mmeany.play.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import net.mmeany.play.app.controller.model.PublishRequest;
import net.mmeany.play.app.controller.model.PublisherRegistrationRequest;
import net.mmeany.play.app.service.HelloService;
import net.mmeany.play.app.service.ZmqService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

    private final HelloService helloService;
    private final ZmqService zmqService;

    public Controller(HelloService helloService, ZmqService zmqService) {

        this.helloService = helloService;
        this.zmqService = zmqService;
    }

    @GetMapping("/hello")
    @Operation(summary = "Say hello")
    public ResponseEntity<?> hello() {

        return ResponseEntity.ok(helloService.sayHello());
    }

    @PostMapping("/register")
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
}
