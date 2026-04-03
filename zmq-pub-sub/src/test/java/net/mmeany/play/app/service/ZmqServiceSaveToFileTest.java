package net.mmeany.play.app.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZmqServiceSaveToFileTest {

    @TempDir
    Path tempDir;

    @Test
    void testSaveToFileDoesNotOverwrite() throws Exception {

        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        String outputDir = tempDir.toAbsolutePath().toString();

        // Use a subclass to override getTimestamp and force a collision
        ZmqService zmqService = new ZmqService(outputDir, false, outputDir, eventPublisher) {
            @Override
            protected long getTimestamp() {

                return 123456789L; // Constant timestamp
            }
        };

        String subscriberName = "testSub";
        String topic = "testTopic";
        byte[] message1 = "message 1".getBytes();
        byte[] message2 = "message 2".getBytes();
        byte[] message3 = "message 3".getBytes();

        java.lang.reflect.Method saveToFile = ZmqService.class.getDeclaredMethod("saveToFile", String.class, String.class, byte[].class);
        saveToFile.setAccessible(true);

        saveToFile.invoke(zmqService, subscriberName, topic, message1);
        saveToFile.invoke(zmqService, subscriberName, topic, message2);
        saveToFile.invoke(zmqService, subscriberName, topic, message3);

        File subDir = new File(outputDir, "test_sub");
        assertTrue(subDir.exists());
        File[] files = subDir.listFiles();
        assert files != null;
        assertEquals(3, files.length, "Should have 3 files");

        List<String> fileNames = Arrays.stream(files).map(File::getName).sorted().toList();
        assertTrue(fileNames.contains("testTopic-123456789.json"));
        assertTrue(fileNames.contains("testTopic-123456789-1.json"));
        assertTrue(fileNames.contains("testTopic-123456789-2.json"));
    }
}
