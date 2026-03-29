package net.mmeany.play.app.controller;

import net.mmeany.play.app.controller.model.*;
import net.mmeany.play.app.util.TestPortUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ControllerEdgeCaseTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {

        registry.add("output-directory", () -> tempDir.toAbsolutePath().toString());
        registry.add("output-directory-clear-on-startup", () -> "true");
        registry.add("workspace-root", () -> tempDir.toAbsolutePath().toString());
    }

    @Test
    void testPublishFilesFromDirectory() throws IOException {

        int port = TestPortUtils.getNextAvailablePort();
        // Register a publisher
        PublisherRegistrationRequest pubReg = new PublisherRegistrationRequest();
        pubReg.setName("dir-pub");
        pubReg.setAddress(TestPortUtils.getBindAddress(port));
        restTemplate.postForEntity("/register-publisher", pubReg, SuccessResponse.class);

        // Create a directory with some files
        Path dir = tempDir.resolve("test-dir");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("file1.txt"), "Content 1");
        Files.writeString(dir.resolve("file2.txt"), "Content 2");

        PublishFilesRequest request = new PublishFilesRequest();
        request.setPublisherName("dir-pub");
        request.setTopic("test-topic");
        request.setDirectory(dir.toAbsolutePath().toString());

        ResponseEntity<SuccessResponse> response = restTemplate.postForEntity("/publish-files", request, SuccessResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
    }

    @Test
    void testPublishFileList() throws IOException {

        int port = TestPortUtils.getNextAvailablePort();
        // Register a publisher
        PublisherRegistrationRequest pubReg = new PublisherRegistrationRequest();
        pubReg.setName("list-pub");
        pubReg.setAddress(TestPortUtils.getBindAddress(port));
        restTemplate.postForEntity("/register-publisher", pubReg, SuccessResponse.class);

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

        ResponseEntity<SuccessResponse> response = restTemplate.postForEntity("/publish-file-list", request, SuccessResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
    }

    @Test
    void testPeriodicPublisherUpdateNonExistent() {

        PeriodicPublisherUpdateRequest request = new PeriodicPublisherUpdateRequest();
        request.setName("non-existent");
        request.setMessage("new message");

        ResponseEntity<SuccessResponse> response = restTemplate.postForEntity("/update-periodic-message", request, SuccessResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess(), "Update should fail for non-existent publisher");
    }

    @Test
    void testEnablePeriodicPublisherNonExistent() {

        PeriodicPublisherStatusRequest request = new PeriodicPublisherStatusRequest();
        request.setName("non-existent");
        request.setEnabled(true);

        ResponseEntity<SuccessResponse> response = restTemplate.postForEntity("/enable-periodic-publisher", request, SuccessResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess(), "Enable should fail for non-existent publisher");
    }

    @Test
    void testUpdatePeriodicFrequencyNonExistent() {

        PeriodicPublisherFrequencyRequest request = new PeriodicPublisherFrequencyRequest();
        request.setName("non-existent");
        request.setPeriod(1000);

        ResponseEntity<SuccessResponse> response = restTemplate.postForEntity("/update-periodic-frequency", request, SuccessResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess(), "Update frequency should fail for non-existent publisher");
    }

    @Test
    void testExecuteLuaInvalidFile() {

        LuaExecutionRequest request = new LuaExecutionRequest();
        request.setFileName("non-existent-file.lua");

        ResponseEntity<LuaExecutionResponse> response = restTemplate.postForEntity("/execute-lua", request, LuaExecutionResponse.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertTrue(response.getBody().getError().contains("Lua script file not found"));
    }

    @Test
    void testExecuteLuaEmptyScript() {

        LuaExecutionRequest request = new LuaExecutionRequest();
        // script and fileName are both null/empty

        ResponseEntity<LuaExecutionResponse> response = restTemplate.postForEntity("/execute-lua", request, LuaExecutionResponse.class);

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

        ResponseEntity<String> response = restTemplate.postForEntity("/publish-files", request, String.class);

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

        ResponseEntity<String> response = restTemplate.postForEntity("/publish-files", request, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("'file' is not a file"));
    }
}
