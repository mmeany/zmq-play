package net.mmeany.play.app.controller.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MonitoredSubscriberRegistrationRequest {
    @NotBlank(message = "Subscriber name is required")
    private String name;
    @NotBlank(message = "Address is required")
    private String address;
    @NotBlank(message = "Topic is required")
    private String topic;
    @Min(value = 1, message = "Watchdog period must be greater than 0")
    private long watchdogPeriod;
    @Min(value = 1, message = "Failure threshold must be greater than 0")
    private int failureThreshold;
    private boolean binary;
}
