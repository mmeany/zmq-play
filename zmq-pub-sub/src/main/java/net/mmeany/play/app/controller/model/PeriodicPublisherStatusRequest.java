package net.mmeany.play.app.controller.model;

import lombok.Data;

@Data
public class PeriodicPublisherStatusRequest {
    private String name;
    private boolean enabled;
}
