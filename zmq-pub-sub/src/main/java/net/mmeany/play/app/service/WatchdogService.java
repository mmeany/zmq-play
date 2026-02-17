package net.mmeany.play.app.service;

import lombok.extern.slf4j.Slf4j;
import net.mmeany.play.app.event.MonitoredSubscriberDownEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class WatchdogService {

    @EventListener
    public void handleMonitoredSubscriberDown(MonitoredSubscriberDownEvent event) {

        log.error("Monitored subscriber {} is DOWN on topic {}", event.getSubscriberName(), event.getTopic());
    }
}
