package net.mmeany.play.app.controller.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PeriodicPublisherUpdateRequest {

    @NotBlank(message = "Publisher name is required")
    private String name;
    private String message;
}
