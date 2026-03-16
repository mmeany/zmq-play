package net.mmeany.play.app.controller.model;

import lombok.Data;

@Data
public class SubscriberRegistrationRequest {
    private String name;
    private String address;
    private boolean binary;
}
