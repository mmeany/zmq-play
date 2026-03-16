package net.mmeany.play.app.controller.model;

import lombok.Data;

@Data
public class PublishFilesRequest {
    private String directory;
    private String file;
    private Long delay;
    private boolean binary;
    private String topic;
    private String publisherName;
}
