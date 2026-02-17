package net.mmeany.play.app.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class MonitoredSubscriberDownEvent extends ApplicationEvent {
    private final String subscriberName;
    private final String topic;

    public MonitoredSubscriberDownEvent(Object source, String subscriberName, String topic) {

        super(source);
        this.subscriberName = subscriberName;
        this.topic = topic;
    }
}
