package net.mmeany.play.app.controller.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeregisterSubscriberRequest {
    @NotBlank(message = "Subscriber name is required")
    private String name;
}
