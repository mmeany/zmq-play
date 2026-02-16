package net.mmeany.play.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.mmeany.play.app.controller.model.PublishRequest;
import net.mmeany.play.app.controller.model.PublisherRegistrationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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

        mockMvc.perform(post("/register")
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
}
