package net.mmeany.play.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.mmeany.play.app.controller.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testRegisterAndPublish() throws Exception {
        // Register a publisher
        PublisherRegistrationRequest regRequest = new PublisherRegistrationRequest();
        regRequest.setName("test-pub");
        regRequest.setAddress("tcp://*:5555");

        mockMvc.perform(post("/register-publisher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(regRequest)))
               .andExpect(status().isOk());

        // Publish a message
        ObjectNode messageNode = objectMapper.createObjectNode();
        messageNode.put("key", "value");
        messageNode.put("number", 123);

        PublishRequest publishRequest = new PublishRequest();
        publishRequest.setPublisherName("test-pub");
        publishRequest.setTopic("test-topic");
        publishRequest.setMessage(messageNode);

        mockMvc.perform(post("/publish")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(publishRequest)))
               .andExpect(status().isOk());
    }

    @Test
    void testSubscriberReceivesMessage() throws Exception {

        String pubAddress = "tcp://127.0.0.1:5556";
        String topic = "test-topic-sub";

        // Register a publisher
        PublisherRegistrationRequest pubReg = new PublisherRegistrationRequest();
        pubReg.setName("pub1");
        pubReg.setAddress("tcp://*:5556");
        mockMvc.perform(post("/register-publisher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(pubReg)))
               .andExpect(status().isOk());

        // Register a subscriber
        SubscriberRegistrationRequest subReg = new SubscriberRegistrationRequest();
        subReg.setName("sub1");
        subReg.setAddress(pubAddress);
        mockMvc.perform(post("/register-subscriber")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(subReg)))
               .andExpect(status().isOk());

        // Wait a bit for connection to establish (ZMQ needs time)
        Thread.sleep(1000);

        // Publish first message
        ObjectNode messageNode1 = objectMapper.createObjectNode();
        messageNode1.put("hello", "subscriber1");

        PublishRequest publishRequest1 = new PublishRequest();
        publishRequest1.setPublisherName("pub1");
        publishRequest1.setTopic(topic);
        publishRequest1.setMessage(messageNode1);

        mockMvc.perform(post("/publish")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(publishRequest1)))
               .andExpect(status().isOk());

        // Publish second message
        ObjectNode messageNode2 = objectMapper.createObjectNode();
        messageNode2.put("hello", "subscriber2");

        PublishRequest publishRequest2 = new PublishRequest();
        publishRequest2.setPublisherName("pub1");
        publishRequest2.setTopic(topic);
        publishRequest2.setMessage(messageNode2);

        mockMvc.perform(post("/publish")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(publishRequest2)))
               .andExpect(status().isOk());

        // Wait for subscriber to receive and write to files
        Thread.sleep(2000);

        // Verify files exist
        File dir = new File(".");
        File[] files = dir.listFiles((d, name) -> name.startsWith(topic) && name.endsWith(".json"));

        try {
            assertTrue(files != null && files.length >= 2, "Subscriber should have created at least two JSON files for the topic");

            // Check contents
            boolean found1 = false;
            boolean found2 = false;
            for (File file : files) {
                String content = Files.readString(file.toPath());
                if (content.contains("subscriber1")) found1 = true;
                if (content.contains("subscriber2")) found2 = true;
            }
            assertTrue(found1, "Should have found file with subscriber1 content");
            assertTrue(found2, "Should have found file with subscriber2 content");
        } finally {
            // Cleanup
            if (files != null) {
                Arrays.stream(files).forEach(File::delete);
            }
        }
    }

    @Test
    void testPeriodicPublisher() throws Exception {

        String topic = "periodic-topic";
        String pubAddress = "tcp://*:5557";
        String subConnectAddress = "tcp://127.0.0.1:5557";

        // Register a subscriber
        SubscriberRegistrationRequest subReg = new SubscriberRegistrationRequest();
        subReg.setName("sub-periodic");
        subReg.setAddress(subConnectAddress);
        mockMvc.perform(post("/register-subscriber")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(subReg)))
               .andExpect(status().isOk());

        // Register a periodic publisher
        ObjectNode initialMessage = objectMapper.createObjectNode();
        initialMessage.put("count", 1);

        PeriodicPublisherRegistrationRequest periodicReg = new PeriodicPublisherRegistrationRequest();
        periodicReg.setName("periodic1");
        periodicReg.setAddress(pubAddress);
        periodicReg.setTopic(topic);
        periodicReg.setMessage(initialMessage);
        periodicReg.setPeriod(500); // 500ms

        mockMvc.perform(post("/register-periodic-publisher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(periodicReg)))
               .andExpect(status().isOk());

        // Wait for at least one message
        Thread.sleep(2000);

        // Update the message
        ObjectNode updatedMessage = objectMapper.createObjectNode();
        updatedMessage.put("count", 2);

        PeriodicPublisherUpdateRequest updateReq = new PeriodicPublisherUpdateRequest();
        updateReq.setName("periodic1");
        updateReq.setMessage(updatedMessage);

        mockMvc.perform(post("/update-periodic-message")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateReq)))
               .andExpect(status().isOk());

        // Wait for updated message
        Thread.sleep(2000);

        // Verify files
        File dir = new File(".");
        File[] files = dir.listFiles((d, name) -> name.startsWith(topic) && name.endsWith(".json"));

        try {
            assertTrue(files != null && files.length >= 2, "Should have received multiple periodic messages");

            boolean foundInitial = false;
            boolean foundUpdated = false;
            for (File file : files) {
                String content = Files.readString(file.toPath());
                if (content.contains("\"count\":1")) foundInitial = true;
                if (content.contains("\"count\":2")) foundUpdated = true;
            }
            assertTrue(foundInitial, "Should have found initial periodic message");
            assertTrue(foundUpdated, "Should have found updated periodic message");
        } finally {
            if (files != null) {
                Arrays.stream(files).forEach(File::delete);
            }
        }
    }
}
