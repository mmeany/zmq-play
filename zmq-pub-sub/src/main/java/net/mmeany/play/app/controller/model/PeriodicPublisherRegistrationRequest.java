package net.mmeany.play.app.controller.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PeriodicPublisherRegistrationRequest {

    @NotBlank(message = "Publisher name is required")
    private String name;
    @NotBlank(message = "Address is required")
    private String address;
    @NotBlank(message = "Topic is required")
    private String topic;
    private String message;
    @Min(value = 1, message = "Period must be greater than 0")
    private long period;
}
