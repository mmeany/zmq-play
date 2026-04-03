package net.mmeany.play.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.mmeany.play.app.controller.model.DeregisterPublisherRequest;
import net.mmeany.play.app.controller.model.DeregisterSubscriberRequest;
import net.mmeany.play.app.controller.model.MonitoredSubscriberRegistrationRequest;
import net.mmeany.play.app.controller.model.PublisherRegistrationRequest;
import net.mmeany.play.app.event.MonitoredSubscriberDownEvent;
import net.mmeany.play.app.util.ConfiguredObjectMapper;
import net.mmeany.play.app.util.TestPortUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MonitoredSubscriberTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = ConfiguredObjectMapper.JSON_MAPPER;

    @Autowired
    private TestEventListener testEventListener;

    @Test
    void testMonitoredSubscriberDownEventRaised() throws Exception {

        int port = TestPortUtils.getNextAvailablePort();
        String subscriberName = "monitored-sub";
        String topic = "heartbeat";
        String address = TestPortUtils.getBindAddress(port);
        String connectAddress = TestPortUtils.getConnectAddress(port);

        // Register a publisher (to avoid connection errors, though SUB usually just waits)
        PublisherRegistrationRequest pubReg = new PublisherRegistrationRequest();
        pubReg.setName("pub-for-monitor");
        pubReg.setAddress(address);
        mockMvc.perform(post("/register-publisher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(pubReg)))
               .andExpect(status().isOk());

        // Register monitored subscriber with short watchdog and low threshold
        MonitoredSubscriberRegistrationRequest subReg = new MonitoredSubscriberRegistrationRequest();
        subReg.setName(subscriberName);
        subReg.setAddress(connectAddress);
        subReg.setTopic(topic);
        subReg.setWatchdogPeriod(500); // 500ms
        subReg.setFailureThreshold(2);

        mockMvc.perform(post("/register-monitored-subscriber")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(subReg)))
               .andExpect(status().isOk());

        // No messages are sent, so after ~1000ms (500ms * 2) it should be considered down
        await().atMost(5, TimeUnit.SECONDS).until(() -> !testEventListener.getEvents().isEmpty());

        boolean eventFound = testEventListener.getEvents().stream()
                                              .anyMatch(e -> e.getSubscriberName().equals(subscriberName) && e.getTopic().equals(topic));
        assertTrue(eventFound, "MonitoredSubscriberDownEvent should have been raised");

        // 3. Deregister monitored subscriber
        DeregisterSubscriberRequest deregisterRequest = new DeregisterSubscriberRequest();
        deregisterRequest.setName(subscriberName);
        mockMvc.perform(post("/deregister-subscriber")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(deregisterRequest)))
               .andExpect(status().isOk());

        // Cleanup
        DeregisterPublisherRequest deregisterPubRequest = new DeregisterPublisherRequest();
        deregisterPubRequest.setName("pub-for-monitor");
        mockMvc.perform(post("/deregister-publisher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(deregisterPubRequest)))
               .andExpect(status().isOk());
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        public TestEventListener testEventListener() {

            return new TestEventListener();
        }
    }

    static class TestEventListener implements ApplicationListener<MonitoredSubscriberDownEvent> {
        private final List<MonitoredSubscriberDownEvent> events = new ArrayList<>();

        @Override
        public void onApplicationEvent(MonitoredSubscriberDownEvent event) {

            events.add(event);
        }

        public List<MonitoredSubscriberDownEvent> getEvents() {

            return events;
        }
    }
}
