package net.mmeany.play.app.service;

import net.mmeany.play.app.controller.model.LuaExecutionResponse;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class LuaService {

    private final ZmqService zmqService;
    private final LuaHelper luaHelper = new LuaHelper();

    public LuaService(ZmqService zmqService) {

        this.zmqService = zmqService;
    }

    public LuaExecutionResponse executeScript(String script) {

        try {
            Globals globals = JsePlatform.standardGlobals();

            // Expose ZmqService to Lua as 'zmq'
            globals.set("zmq", CoerceJavaToLua.coerce(new ZmqServiceLuaWrapper(zmqService)));

            // Expose LuaHelper to Lua as 'helper'
            globals.set("helper", CoerceJavaToLua.coerce(luaHelper));

            LuaValue chunk = globals.load(script);
            LuaValue result = chunk.call();

            return LuaExecutionResponse.builder()
                                       .success(true)
                                       .result(result.toString())
                                       .build();
        } catch (Exception e) {
            return LuaExecutionResponse.builder()
                                       .success(false)
                                       .error(e.getMessage())
                                       .build();
        }
    }

    /**
     * A wrapper around ZmqService to provide some Lua-friendly helpers
     * or to ensure correct type coercion if Luaj's default is not enough.
     */
    public static class ZmqServiceLuaWrapper {
        private final ZmqService zmqService;

        public ZmqServiceLuaWrapper(ZmqService zmqService) {

            this.zmqService = zmqService;
        }

        public void registerPublisher(String name, String address) {

            zmqService.registerPublisher(name, address);
        }

        public void deregisterPublisher(String name) {

            zmqService.deregisterPublisher(name);
        }

        public void publish(String publisherName, String topic, String message) {

            zmqService.publish(publisherName, topic, message.getBytes(StandardCharsets.UTF_8));
        }

        public void registerSubscriber(String subscriberName, String address, boolean binary) {

            zmqService.registerSubscriber(subscriberName, address, binary);
        }

        public void registerPeriodicPub(String name, String address, String topic, String message, long period) {

            zmqService.registerPeriodicPublisher(name, address, topic, message, period);
        }

        public void registerMonitoredSub(String name, String address, String topic, long watchdogPeriod, int failureThreshold, boolean binary) {

            zmqService.registerMonitoredSubscriber(name, address, topic, watchdogPeriod, failureThreshold, binary);
        }

        public void updatePeriodicMsg(String name, String newMessage) {

            zmqService.updatePeriodicMessage(name, newMessage);
        }

        public void enablePeriodicPub(String name, boolean enabled) {

            zmqService.enablePeriodicPublisher(name, enabled);
        }

        public void updatePeriodicFreq(String name, long period) {

            zmqService.updatePeriodicFrequency(name, period);
        }

        public LuaValue listPublishers() {

            List<net.mmeany.play.app.controller.model.PublisherDetails> list = zmqService.listPublishers();
            LuaTable table = new LuaTable();
            for (int i = 0; i < list.size(); i++) {
                table.set(i + 1, CoerceJavaToLua.coerce(list.get(i)));
            }
            return table;
        }

        /**
         * Helper for publishFiles that takes a Lua table (coerced to LuaValue) or a single string
         */
        public void publishFiles(String publisherName, String topic, Object filePaths, long delayMs, boolean binary) {

            List<File> files = new ArrayList<>();
            if (filePaths instanceof LuaValue lv && lv.istable()) {
                int length = lv.length();
                for (int i = 1; i <= length; i++) {
                    files.add(new File(lv.get(i).tojstring()));
                }
            } else {
                files.add(new File(filePaths.toString()));
            }
            zmqService.publishFiles(publisherName, topic, files, delayMs, binary);
        }

        /**
         * Alias for publishFiles to support older scripts
         */
        public void pubFiles(String publisherName, String topic, Object filePaths, long delayMs, boolean binary) {
            publishFiles(publisherName, topic, filePaths, delayMs, binary);
        }

        public void publishFileList(String publisherName, String topic, String directory, LuaValue fileNames, long delayMs, boolean binary) {

            List<String> names = new ArrayList<>();
            if (fileNames.istable()) {
                int length = fileNames.length();
                for (int i = 1; i <= length; i++) {
                    names.add(fileNames.get(i).tojstring());
                }
            } else {
                names.add(fileNames.tojstring());
            }
            zmqService.publishFileList(publisherName, topic, directory, names, delayMs, binary);
        }

        public void pubFileList(String publisherName, String topic, String directory, LuaValue fileNames, long delayMs, boolean binary) {
            publishFileList(publisherName, topic, directory, fileNames, delayMs, binary);
        }
    }
}
