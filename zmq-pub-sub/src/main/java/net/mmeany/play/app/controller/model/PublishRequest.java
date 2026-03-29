package net.mmeany.play.app.controller.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PublishRequest {
    @NotBlank(message = "Publisher name is required")
    private String publisherName;
    @NotBlank(message = "Topic is required")
    private String topic;
    private String message;
}
