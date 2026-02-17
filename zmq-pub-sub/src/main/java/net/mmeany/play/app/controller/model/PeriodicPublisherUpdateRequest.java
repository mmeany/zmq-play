package net.mmeany.play.app.controller.model;

import lombok.Data;

@Data
public class PeriodicPublisherUpdateRequest {

    private String name;
    private Object message;
}
