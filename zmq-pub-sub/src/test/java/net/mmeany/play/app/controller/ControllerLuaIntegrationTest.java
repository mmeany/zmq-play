package net.mmeany.play.app.controller;

import net.mmeany.play.app.controller.model.DeregisterPublisherRequest;
import net.mmeany.play.app.controller.model.LuaExecutionRequest;
import net.mmeany.play.app.controller.model.LuaExecutionResponse;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ControllerLuaIntegrationTest {

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
    void testExecuteLuaIntegrationScript() throws IOException {

        int port1 = TestPortUtils.getNextAvailablePort();
        int port2 = TestPortUtils.getNextAvailablePort();
        String address = TestPortUtils.getBindAddress(port1);
        String periodicAddress = TestPortUtils.getBindAddress(port2);
        String connectAddress = TestPortUtils.getConnectAddress(port1);
        String periodicConnectAddress = TestPortUtils.getConnectAddress(port2);

        // Create a temporary file to publish via Lua
        Path testFile = tempDir.resolve("test-file.bin");
        Files.write(testFile, "Hello World from Temp File".getBytes(StandardCharsets.UTF_8));
        String testFilePath = testFile.toAbsolutePath().toString().replace("\\", "/");

        // Read the lua script from resources
        String script;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("integration_test.lua")) {
            assertNotNull(is, "integration_test.lua not found in classpath");
            script = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        // Inject variables into the script
        script = "local address = '" + address + "';\n" +
                "local periodicAddress = '" + periodicAddress + "';\n" +
                "local connectAddress = '" + connectAddress + "';\n" +
                "local periodicConnectAddress = '" + periodicConnectAddress + "';\n" +
                "local testFilePath = '" + testFilePath + "';\n" + script;

        LuaExecutionRequest request = new LuaExecutionRequest();
        request.setScript(script);

        ResponseEntity<LuaExecutionResponse> response = restTemplate.postForEntity("/execute-lua", request, LuaExecutionResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(), "Request failed with body: " + (response.getBody() != null ? response.getBody().getError() : "null"));
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess(), "Lua script failed: " + response.getBody().getError());
        assertEquals("Lua script completed successfully", response.getBody().getResult());

        // Verify that standard subscriber received the message
        File subDir = new File(tempDir.toFile(), "test_sub");
        assertTrue(subDir.exists() && subDir.isDirectory(), "Subscriber directory should exist: " + subDir.getAbsolutePath());
        File[] files = subDir.listFiles((d, name) -> name.endsWith(".json"));
        assertTrue(files != null && files.length >= 1, "Should have received at least one message in test_sub");

        // Verify that monitored subscriber received messages
        File monitoredSubDir = new File(tempDir.toFile(), "monitored_sub");
        assertTrue(monitoredSubDir.exists() && monitoredSubDir.isDirectory(), "Monitored subscriber directory should exist: " + monitoredSubDir.getAbsolutePath());
        File[] monitoredFiles = monitoredSubDir.listFiles((d, name) -> name.endsWith(".json"));
        assertTrue(monitoredFiles != null && monitoredFiles.length >= 1, "Should have received at least one message in monitored_sub");
    }

    @Test
    void testExecuteLuaFromFile() throws IOException {

        int port = TestPortUtils.getNextAvailablePort();
        String address = TestPortUtils.getBindAddress(port);
        // Create a temporary lua script that doesn't need external variables
        Path luaScript = tempDir.resolve("test.lua");
        String scriptContent = "zmq:registerPublisher('file-pub', '" + address + "'); return 'Script from file executed'";
        Files.write(luaScript, scriptContent.getBytes(StandardCharsets.UTF_8));

        LuaExecutionRequest request = new LuaExecutionRequest();
        request.setFileName(luaScript.toAbsolutePath().toString());

        ResponseEntity<LuaExecutionResponse> response = restTemplate.postForEntity("/execute-lua", request, LuaExecutionResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(), "Request failed with body: " + (response.getBody() != null ? response.getBody().getError() : "null"));
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess(), "Lua script from file failed: " + response.getBody().getError());
        assertEquals("Script from file executed", response.getBody().getResult());

        // Cleanup
        DeregisterPublisherRequest deregisterRequest = new DeregisterPublisherRequest();
        deregisterRequest.setName("file-pub");
        restTemplate.postForEntity("/deregister-publisher", deregisterRequest, Void.class);
    }
}
