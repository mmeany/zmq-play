package net.mmeany.play.app.controller.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PeriodicPublisherFrequencyRequest {
    @NotBlank(message = "Publisher name is required")
    private String name;
    @Min(value = 1, message = "Period must be greater than 0")
    private long period;
}
