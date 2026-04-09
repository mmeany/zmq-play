package net.mmeany.play.app.controller;

import net.mmeany.play.app.controller.model.*;
import net.mmeany.play.app.util.TestPortUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ControllerEdgeCaseTest {

    @LocalServerPort
    private int serverPort;

    private RestClient restClient;

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {

        registry.add("output-directory", () -> tempDir.toAbsolutePath().toString());
        registry.add("output-directory-clear-on-startup", () -> "true");
        registry.add("workspace-root", () -> tempDir.toAbsolutePath().toString());
    }

    @BeforeEach
    void setUpClient() {

        this.restClient = RestClient.builder().baseUrl("http://localhost:" + serverPort).build();
    }

    private <T> ResponseEntity<T> postForEntity(String uri, Object request, Class<T> responseType) {

        try {
            return restClient.post().uri(uri).body(request).retrieve().toEntity(responseType);
        } catch (HttpStatusCodeException ex) {
            T body = null;
            if (responseType == String.class) {
                body = responseType.cast(ex.getResponseBodyAsString());
            } else {
                try {
                    body = net.mmeany.play.app.util.ConfiguredObjectMapper.JSON_MAPPER.readValue(ex.getResponseBodyAsString(), responseType);
                } catch (Exception ignored) {
                    // no-op
                }
            }
            return new ResponseEntity<>(body, ex.getStatusCode());
        }
    }

    @Test
    void testPublishFilesFromDirectory() throws IOException {

        int publisherPort = TestPortUtils.getNextAvailablePort();
        // Register a publisher
        PublisherRegistrationRequest pubReg = new PublisherRegistrationRequest();
        pubReg.setName("dir-pub");
        pubReg.setAddress(TestPortUtils.getBindAddress(publisherPort));
        postForEntity("/register-publisher", pubReg, SuccessResponse.class);

        // Create a directory with some files
        Path dir = tempDir.resolve("test-dir");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("file1.txt"), "Content 1");
        Files.writeString(dir.resolve("file2.txt"), "Content 2");

        PublishFilesRequest request = new PublishFilesRequest();
        request.setPublisherName("dir-pub");
        request.setTopic("test-topic");
        request.setDirectory(dir.toAbsolutePath().toString());

        ResponseEntity<SuccessResponse> response = postForEntity("/publish-files", request, SuccessResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
    }

    @Test
    void testPublishFileList() throws IOException {

        int publisherPort = TestPortUtils.getNextAvailablePort();
        // Register a publisher
        PublisherRegistrationRequest pubReg = new PublisherRegistrationRequest();
        pubReg.setName("list-pub");
        pubReg.setAddress(TestPortUtils.getBindAddress(publisherPort));
        postForEntity("/register-publisher", pubReg, SuccessResponse.class);

        // Create a directory with some files
        Path dir = tempDir.resolve("list-dir");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("fileA.txt"), "Content A");
        Files.writeString(dir.resolve("fileB.txt"), "Content B");
        Files.writeString(dir.resolve("fileC.txt"), "Content C");

        PublishFileListRequest request = new PublishFileListRequest();
        request.setPublisherName("list-pub");
        request.setTopic("test-topic");
        request.setDirectory(dir.toAbsolutePath().toString());
        request.setFiles(Arrays.asList("fileA.txt", "fileC.txt"));

        ResponseEntity<SuccessResponse> response = postForEntity("/publish-file-list", request, SuccessResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
    }

    @Test
    void testPeriodicPublisherUpdateNonExistent() {

        PeriodicPublisherUpdateRequest request = new PeriodicPublisherUpdateRequest();
        request.setName("non-existent");
        request.setMessage("new message");

        ResponseEntity<SuccessResponse> response = postForEntity("/update-periodic-message", request, SuccessResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess(), "Update should fail for non-existent publisher");
    }

    @Test
    void testEnablePeriodicPublisherNonExistent() {

        PeriodicPublisherStatusRequest request = new PeriodicPublisherStatusRequest();
        request.setName("non-existent");
        request.setEnabled(true);

        ResponseEntity<SuccessResponse> response = postForEntity("/enable-periodic-publisher", request, SuccessResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess(), "Enable should fail for non-existent publisher");
    }

    @Test
    void testUpdatePeriodicFrequencyNonExistent() {

        PeriodicPublisherFrequencyRequest request = new PeriodicPublisherFrequencyRequest();
        request.setName("non-existent");
        request.setPeriod(1000);

        ResponseEntity<SuccessResponse> response = postForEntity("/update-periodic-frequency", request, SuccessResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess(), "Update frequency should fail for non-existent publisher");
    }

    @Test
    void testExecuteLuaInvalidFile() {

        LuaExecutionRequest request = new LuaExecutionRequest();
        request.setFileName("non-existent-file.lua");

        ResponseEntity<LuaExecutionResponse> response = postForEntity("/execute-lua", request, LuaExecutionResponse.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertTrue(response.getBody().getError().contains("Lua script file not found"));
    }

    @Test
    void testExecuteLuaEmptyScript() {

        LuaExecutionRequest request = new LuaExecutionRequest();
        // script and fileName are both null/empty

        ResponseEntity<LuaExecutionResponse> response = postForEntity("/execute-lua", request, LuaExecutionResponse.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertTrue(response.getBody().getError() != null && response.getBody().getError().contains("Either 'script' or 'fileName' must be provided"));
    }

    @Test
    void testPublishFilesInvalidDirectory() {

        PublishFilesRequest request = new PublishFilesRequest();
        request.setPublisherName("any-pub");
        request.setTopic("any-topic");
        request.setDirectory("not-a-real-directory-12345");

        ResponseEntity<String> response = postForEntity("/publish-files", request, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("'directory' is not a directory"));
    }

    @Test
    void testPublishFilesInvalidFile() {

        PublishFilesRequest request = new PublishFilesRequest();
        request.setPublisherName("any-pub");
        request.setTopic("any-topic");
        request.setFile("not-a-real-file-12345.txt");

        ResponseEntity<String> response = postForEntity("/publish-files", request, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("'file' is not a file"));
    }

    @Test
    void testPeriodicPublisherWithoutMessage() {

        int publisherPort = TestPortUtils.getNextAvailablePort();
        String pubName = "no-msg-periodic-pub";

        // 1. Register without message
        PeriodicPublisherRegistrationRequest regRequest = new PeriodicPublisherRegistrationRequest();
        regRequest.setName(pubName);
        regRequest.setAddress(TestPortUtils.getBindAddress(publisherPort));
        regRequest.setTopic("periodic-topic");
        regRequest.setPeriod(1000);
        // message is null

        ResponseEntity<SuccessResponse> regResponse = postForEntity("/register-periodic-publisher", regRequest, SuccessResponse.class);

        assertEquals(HttpStatus.OK, regResponse.getStatusCode());
        assertNotNull(regResponse.getBody());
        assertTrue(regResponse.getBody().isSuccess(), "Registration should succeed without message");

        // 2. Update without message
        PeriodicPublisherUpdateRequest updateRequest = new PeriodicPublisherUpdateRequest();
        updateRequest.setName(pubName);
        // message is null

        ResponseEntity<SuccessResponse> updateResponse = postForEntity("/update-periodic-message", updateRequest, SuccessResponse.class);

        assertEquals(HttpStatus.OK, updateResponse.getStatusCode());
        assertNotNull(updateResponse.getBody());
        assertTrue(updateResponse.getBody().isSuccess(), "Update should succeed without message");
    }

    @Test
    void testPublishWithoutMessage() {

        int publisherPort = TestPortUtils.getNextAvailablePort();
        String pubName = "no-msg-pub";

        // Register a normal publisher
        PublisherRegistrationRequest pubReg = new PublisherRegistrationRequest();
        pubReg.setName(pubName);
        pubReg.setAddress(TestPortUtils.getBindAddress(publisherPort));
        postForEntity("/register-publisher", pubReg, SuccessResponse.class);

        // Publish without message
        PublishRequest request = new PublishRequest();
        request.setPublisherName(pubName);
        request.setTopic("some-topic");
        // message is null

        ResponseEntity<SuccessResponse> response = postForEntity("/publish", request, SuccessResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess(), "Publish should succeed without message");
    }
}
