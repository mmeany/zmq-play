package net.mmeany.play.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import net.mmeany.play.app.service.HelloService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

    private final HelloService helloService;

    public Controller(HelloService helloService) {
        this.helloService = helloService;
    }

    @GetMapping("/hello")
    @Operation(summary = "Say hello")
    public ResponseEntity<?> hello() {
        return ResponseEntity.ok(helloService.sayHello());
    }
}
