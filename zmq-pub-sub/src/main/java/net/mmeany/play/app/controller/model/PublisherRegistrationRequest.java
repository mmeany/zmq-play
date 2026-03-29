package net.mmeany.play.app.controller.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PublisherRegistrationRequest {
    @NotBlank(message = "Publisher name is required")
    private String name;
    @NotBlank(message = "Address is required")
    private String address;
}
