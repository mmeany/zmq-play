package net.mmeany.play.app.controller.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class PublishFileListRequest {
    @NotBlank(message = "Publisher name is required")
    private String publisherName;
    @NotBlank(message = "Topic is required")
    private String topic;
    @NotBlank(message = "Directory is required")
    private String directory;
    @NotEmpty(message = "Files list cannot be empty")
    private List<String> files;
    @Min(value = 0, message = "Delay cannot be negative")
    private long delay;
    private boolean binary;
}
