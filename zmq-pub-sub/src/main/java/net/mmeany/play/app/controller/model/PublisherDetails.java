package net.mmeany.play.app.controller.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PublisherDetails {
    private String name;
    private String type;
    private String address;
    private String topic;
    private String message;
    private Long period;
    private Boolean enabled;
}
