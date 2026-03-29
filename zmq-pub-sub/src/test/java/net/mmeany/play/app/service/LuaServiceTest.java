package net.mmeany.play.app.service;

import net.mmeany.play.app.controller.model.LuaExecutionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LuaServiceTest {

    @Mock
    private ZmqService zmqService;

    private LuaService luaService;

    @BeforeEach
    void setUp() {

        lenient().when(zmqService.validatePath(anyString())).thenAnswer(invocation -> Paths.get((String) invocation.getArgument(0)));
        luaService = new LuaService(zmqService);
    }

    @Test
    void testExecuteSimpleScript() {

        LuaExecutionResponse response = luaService.executeScript("return 1 + 1");
        assertTrue(response.isSuccess());
        assertEquals("2", response.getResult());
    }

    @Test
    void testExecuteZmqRegistration() {

        String script = "zmq:registerPublisher('test-pub', 'tcp://*:5555'); return 'ok'";
        LuaExecutionResponse response = luaService.executeScript(script);
        assertTrue(response.isSuccess(), "Error: " + response.getError());
        assertEquals("ok", response.getResult());
        verify(zmqService).registerPublisher("test-pub", "tcp://*:5555");
    }

    @Test
    void testExecuteZmqPublish() {

        String script = "zmq:publish('test-pub', 'test-topic', 'hello'); return 'ok'";
        LuaExecutionResponse response = luaService.executeScript(script);
        assertTrue(response.isSuccess(), "Error: " + response.getError());
        assertEquals("ok", response.getResult());
        verify(zmqService).publish(eq("test-pub"), eq("test-topic"), any(byte[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testExecuteZmqPublishFiles() {

        String script = "zmq:publishFiles('test-pub', 'test-topic', {'file1.txt', 'file2.txt'}, 100, true); return 'ok'";
        LuaExecutionResponse response = luaService.executeScript(script);
        assertTrue(response.isSuccess(), "Error: " + response.getError());
        assertEquals("ok", response.getResult());
        verify(zmqService).publishFiles(eq("test-pub"), eq("test-topic"), any(List.class), eq(100L), eq(true));
    }

    @Test
    void testExecuteLuaSleep() {

        String script = "helper:sleep(100); return 'done'";
        long start = System.currentTimeMillis();
        LuaExecutionResponse response = luaService.executeScript(script);
        long end = System.currentTimeMillis();

        assertTrue(response.isSuccess());
        assertEquals("done", response.getResult());
        assertTrue(end - start >= 100, "Should have slept at least 100ms");
    }

    @Test
    void testInvalidScript() {

        LuaExecutionResponse response = luaService.executeScript("invalid lua code");
        assertFalse(response.isSuccess());
        assertNotNull(response.getError());
    }
}
