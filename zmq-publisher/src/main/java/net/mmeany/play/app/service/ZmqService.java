package net.mmeany.play.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.mmeany.play.app.util.ConfiguredObjectMapper;
import org.springframework.stereotype.Service;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class ZmqService {

    private final ZContext zContext = new ZContext(1);
    private final Map<String, ZMQ.Socket> publishers = new HashMap<>();

    @PreDestroy
    public void shutdown() {

        for (ZMQ.Socket publisher : publishers.values()) {
            publisher.close();
        }
        zContext.close();
    }

    /**
     * Adds a new publisher to the ZMQ service.
     *
     * @param name    The name of the publisher.
     * @param address The address to bind the publisher to.
     */
    public void registerPublisher(String name, String address) {

        log.info("Adding publisher {} on {}", name, address);
        publishers.put(name, zContext.createSocket(SocketType.PUB));
        publishers.get(name).bind(address);
    }

    /**
     * Publishes a message to a publisher.
     *
     * @param publisherName name of registered publisher
     * @param topic         name of topic to publish to
     * @param data          data to publish - will be serialized to JSON
     */
    public void publish(String publisherName, String topic, Object data) {

        if (!publishers.containsKey(publisherName)) {
            throw new IllegalArgumentException("Unknown publisher: " + publisherName);
        }
        ZMQ.Socket publisher = publishers.get(publisherName);
        try {
            //while (!Thread.currentThread().isInterrupted()) {
            String json = ConfiguredObjectMapper.JSON_MAPPER.writeValueAsString(data);
            publisher.send(topic, ZMQ.SNDMORE);
            publisher.send(json, 0);
            //}
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
