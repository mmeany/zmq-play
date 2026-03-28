package net.mmeany.play.app.controller.model;

import lombok.Data;

import java.util.List;

@Data
public class PublishFileListRequest {
    private String publisherName;
    private String topic;
    private String directory;
    private List<String> files;
    private long delay;
    private boolean binary;
}
