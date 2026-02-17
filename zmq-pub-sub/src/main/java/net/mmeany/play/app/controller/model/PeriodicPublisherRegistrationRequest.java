package net.mmeany.play.app.controller.model;

import lombok.Data;

@Data
public class PeriodicPublisherRegistrationRequest {

    private String name;
    private String address;
    private String topic;
    private String message;
    private long period;
}
