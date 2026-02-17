package net.mmeany.play.app.controller.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class PublishRequest {
    private String publisherName;
    private String topic;
    private JsonNode message;
}
