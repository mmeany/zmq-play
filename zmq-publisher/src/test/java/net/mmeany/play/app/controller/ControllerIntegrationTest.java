package net.mmeany.play.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.mmeany.play.app.controller.model.PublishRequest;
import net.mmeany.play.app.controller.model.PublisherRegistrationRequest;
import net.mmeany.play.app.controller.model.SubscriberRegistrationRequest;
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
        // ... (existing code)
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
}
