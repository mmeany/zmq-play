package net.mmeany.play.app.controller.model;

import lombok.Data;

@Data
public class MonitoredSubscriberRegistrationRequest {
    private String name;
    private String address;
    private String topic;
    private long watchdogPeriod;
    private int failureThreshold;
    private boolean binary;
}
