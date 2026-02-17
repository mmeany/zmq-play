package net.mmeany.play.app.controller.model;

import lombok.Data;

@Data
public class PublishRequest {
    private String publisherName;
    private String topic;
    private String message;
}
