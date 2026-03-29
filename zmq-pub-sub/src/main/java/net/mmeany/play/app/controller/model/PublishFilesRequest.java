package net.mmeany.play.app.controller.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PublishFilesRequest {
    @NotBlank(message = "Directory is required")
    private String directory;
    @NotBlank(message = "File pattern is required")
    private String file;
    @Min(value = 0, message = "Delay cannot be negative")
    private Long delay;
    private boolean binary;
    @NotBlank(message = "Topic is required")
    private String topic;
    @NotBlank(message = "Publisher name is required")
    private String publisherName;
}
