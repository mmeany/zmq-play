package net.mmeany.play.app.controller.model;

import lombok.Data;

@Data
public class PeriodicPublisherFrequencyRequest {
    private String name;
    private long period;
}
