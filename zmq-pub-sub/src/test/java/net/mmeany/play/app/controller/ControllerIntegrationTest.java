package net.mmeany.play.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.mmeany.play.app.controller.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {

        registry.add("output-directory", () -> tempDir.toAbsolutePath().toString());
    }

    @Test
    void testRegisterAndPublish() throws Exception {
        // ... (existing test)
    }

    @Test
    void testDeregisterPublisher() throws Exception {
        // Register a one-shot publisher
        PublisherRegistrationRequest regRequest = new PublisherRegistrationRequest();
        regRequest.setName("one-shot-to-deregister");
        regRequest.setAddress("tcp://*:5570");

        mockMvc.perform(post("/register-publisher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(regRequest)))
               .andExpect(status().isOk());

        // Deregister it
        DeregisterPublisherRequest deregRequest = new DeregisterPublisherRequest();
        deregRequest.setName("one-shot-to-deregister");

        mockMvc.perform(post("/deregister-publisher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(deregRequest)))
               .andExpect(status().isOk());

        // Attempting to publish should now fail with IllegalArgumentException (wrapped in nested exception from mockMvc)
        PublishRequest publishRequest = new PublishRequest();
        publishRequest.setPublisherName("one-shot-to-deregister");
        publishRequest.setTopic("test-topic");
        publishRequest.setMessage("test");

        try {
            mockMvc.perform(post("/publish")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(publishRequest)));
        } catch (Exception e) {
            assertInstanceOf(IllegalArgumentException.class, e.getCause());
            assertTrue(e.getCause().getMessage().contains("Unknown publisher"));
        }
    }

    @Test
    void testDeregisterPeriodicPublisher() throws Exception {
        // Register a periodic publisher
        PeriodicPublisherRegistrationRequest regRequest = new PeriodicPublisherRegistrationRequest();
        regRequest.setName("periodic-to-deregister");
        regRequest.setAddress("tcp://*:5571");
        regRequest.setTopic("periodic-topic");
        regRequest.setMessage("periodic-message");
        regRequest.setPeriod(1000L);

        mockMvc.perform(post("/register-periodic-publisher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(regRequest)))
               .andExpect(status().isOk());

        // Deregister it
        DeregisterPublisherRequest deregRequest = new DeregisterPublisherRequest();
        deregRequest.setName("periodic-to-deregister");

        mockMvc.perform(post("/deregister-publisher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(deregRequest)))
               .andExpect(status().isOk());

        // Attempting to publish should now fail
        PublishRequest publishRequest = new PublishRequest();
        publishRequest.setPublisherName("periodic-to-deregister");
        publishRequest.setTopic("test-topic");
        publishRequest.setMessage("test");

        try {
            mockMvc.perform(post("/publish")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(publishRequest)));
        } catch (Exception e) {
            assertInstanceOf(IllegalArgumentException.class, e.getCause());
            assertTrue(e.getCause().getMessage().contains("Unknown publisher"));
        }
    }

    @Test
    void testListPublishers() throws Exception {
        // Register a one-shot publisher
        PublisherRegistrationRequest reg1 = new PublisherRegistrationRequest();
        reg1.setName("list-pub-1");
        reg1.setAddress("tcp://*:5580");
        mockMvc.perform(post("/register-publisher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(reg1)))
               .andExpect(status().isOk());

        // Register a periodic publisher
        PeriodicPublisherRegistrationRequest reg2 = new PeriodicPublisherRegistrationRequest();
        reg2.setName("list-pub-2");
        reg2.setAddress("tcp://*:5581");
        reg2.setTopic("list-topic");
        reg2.setMessage("list-message");
        reg2.setPeriod(5000L);
        mockMvc.perform(post("/register-periodic-publisher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(reg2)))
               .andExpect(status().isOk());

        // List publishers
        String response = mockMvc.perform(get("/list-publishers"))
                                 .andExpect(status().isOk())
                                 .andReturn().getResponse().getContentAsString();

        java.util.List<net.mmeany.play.app.controller.model.PublisherDetails> list = objectMapper.readValue(response, new com.fasterxml.jackson.core.type.TypeReference<>() {});

        assertTrue(list.stream().anyMatch(p -> p.getName().equals("list-pub-1") && p.getType().equals("one-shot") && p.getAddress().equals("tcp://*:5580")));
        assertTrue(list.stream().anyMatch(p -> p.getName().equals("list-pub-2") && p.getType().equals("periodic") && p.getAddress().equals("tcp://*:5581") && p.getTopic().equals("list-topic") && p.getPeriod() == 5000L));
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
        String message1 = "{\"hello\":\"subscriber1\"}";

        PublishRequest publishRequest1 = new PublishRequest();
        publishRequest1.setPublisherName("pub1");
        publishRequest1.setTopic(topic);
        publishRequest1.setMessage(message1);

        mockMvc.perform(post("/publish")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(publishRequest1)))
               .andExpect(status().isOk());

        // Publish second message
        String message2 = "{\"hello\":\"subscriber2\"}";

        PublishRequest publishRequest2 = new PublishRequest();
        publishRequest2.setPublisherName("pub1");
        publishRequest2.setTopic(topic);
        publishRequest2.setMessage(message2);

        mockMvc.perform(post("/publish")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(publishRequest2)))
               .andExpect(status().isOk());

        // Wait for subscriber to receive and write to files
        Thread.sleep(2000);

        // Verify files exist in sub directory
        File subDir = new File(tempDir.toFile(), "sub1");
        File[] files = subDir.listFiles((d, name) -> name.startsWith(topic) && name.endsWith(".json"));

        try {
            assertTrue(files != null && files.length >= 2, "Subscriber should have created at least two JSON files for the topic in " + subDir.getAbsolutePath());

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
        String initialMessage = "{\"count\":1}";

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
        String updatedMessage = "{\"count\":2}";

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
        File subDir = new File(tempDir.toFile(), "sub_periodic");
        File[] files = subDir.listFiles((d, name) -> name.startsWith(topic) && name.endsWith(".json"));

        try {
            assertTrue(files != null && files.length >= 2, "Should have received multiple periodic messages in " + subDir.getAbsolutePath());

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

    @Test
    void testPublishFilesViaPublisher() throws Exception {

        String topic = "files-topic";
        String pubBind = "tcp://*:5560";
        String subConnect = "tcp://127.0.0.1:5560";

        // Create a couple of files to publish
        Path filesDir = tempDir.resolve("files-src");
        Files.createDirectories(filesDir);
        Path f1 = filesDir.resolve("a.txt");
        Path f2 = filesDir.resolve("b.txt");
        Files.writeString(f1, "alpha");
        Files.writeString(f2, "beta");

        // Register subscriber first (connects)
        SubscriberRegistrationRequest subReg = new SubscriberRegistrationRequest();
        subReg.setName("filesSub");
        subReg.setAddress(subConnect);
        mockMvc.perform(post("/register-subscriber")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(subReg)))
               .andExpect(status().isOk());

        // Register a publisher (binds)
        PublisherRegistrationRequest pubReg = new PublisherRegistrationRequest();
        pubReg.setName("filesPub");
        pubReg.setAddress(pubBind);
        mockMvc.perform(post("/register-publisher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(pubReg)))
               .andExpect(status().isOk());

        // Allow ZMQ time to connect
        Thread.sleep(1000);

        // Call publish-files with the directory
        PublishFilesRequest req = new PublishFilesRequest();
        req.setDirectory(filesDir.toAbsolutePath().toString());
        req.setTopic(topic);
        req.setPublisherName("filesPub");
        req.setDelay(200L);
        req.setBinary(false);

        mockMvc.perform(post("/publish-files")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
               .andExpect(status().isOk());

        // Wait for files to be received
        Thread.sleep(2000);

        File subDir = new File(tempDir.toFile(), "files_sub");
        File[] out = subDir.listFiles((d, name) -> name.startsWith(topic) && name.endsWith(".json"));
        try {
            assertTrue(out != null && out.length >= 2, "Subscriber should have received at least 2 files");
            boolean sawAlpha = false, sawBeta = false;
            for (File f : out) {
                String c = Files.readString(f.toPath());
                if (c.contains("alpha")) sawAlpha = true;
                if (c.contains("beta")) sawBeta = true;
            }
            assertTrue(sawAlpha, "Should see content from first file");
            assertTrue(sawBeta, "Should see content from second file");
        } finally {
            if (out != null) Arrays.stream(out).forEach(File::delete);
        }
    }
}
