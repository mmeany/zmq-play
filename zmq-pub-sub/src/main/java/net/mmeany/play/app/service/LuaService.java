package net.mmeany.play.app.service;

import net.mmeany.play.app.controller.model.LuaExecutionResponse;
import net.mmeany.play.app.controller.model.PublisherDetails;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.*;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.JseBaseLib;
import org.luaj.vm2.lib.jse.JseMathLib;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
            // Create sandboxed globals
            Globals globals = new Globals();
            globals.load(new JseBaseLib());
            globals.load(new PackageLib());
            globals.load(new Bit32Lib());
            globals.load(new TableLib());
            globals.load(new StringLib());
            globals.load(new CoroutineLib());
            globals.load(new JseMathLib());
            // Exclude IoLib, OsLib, and Luajava for security

            LoadState.install(globals);
            LuaC.install(globals);

            // Expose ZmqService to Lua as 'zmq'
            globals.set("zmq", CoerceJavaToLua.coerce(new ZmqServiceLuaWrapper(zmqService)));

            // Expose LuaHelper to Lua as 'helper'
            globals.set("helper", CoerceJavaToLua.coerce(luaHelper));

            LuaValue chunk = globals.load(script);
            LuaValue result = chunk.call();

            return LuaExecutionResponse.builder()
                                       .success(true)
                                       .result(result.isnil() ? "nil" : result.toString())
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

        public boolean registerPublisher(String name, String address) {

            return zmqService.registerPublisher(name, address);
        }

        public boolean deregisterPublisher(String name) {

            return zmqService.deregisterPublisher(name);
        }

        public boolean publish(String publisherName, String topic, String message) {

            byte[] data = (message != null) ? message.getBytes(StandardCharsets.UTF_8) : new byte[0];
            return zmqService.publish(publisherName, topic, data);
        }

        public boolean registerSubscriber(String subscriberName, String address, boolean binary) {

            return zmqService.registerSubscriber(subscriberName, address, binary);
        }

        public boolean registerPeriodicPub(String name, String address, String topic, String message, long period) {

            return zmqService.registerPeriodicPublisher(name, address, topic, message, period);
        }

        public boolean registerMonitoredSub(String name, String address, String topic, long watchdogPeriod, int failureThreshold, boolean binary) {

            return zmqService.registerMonitoredSubscriber(name, address, topic, watchdogPeriod, failureThreshold, binary);
        }

        public boolean updatePeriodicMsg(String name, String newMessage) {

            return zmqService.updatePeriodicMessage(name, newMessage);
        }

        public boolean enablePeriodicPub(String name, boolean enabled) {

            return zmqService.enablePeriodicPublisher(name, enabled);
        }

        public boolean updatePeriodicFreq(String name, long period) {

            return zmqService.updatePeriodicFrequency(name, period);
        }

        public LuaValue listPublishers() {

            List<PublisherDetails> list = zmqService.listPublishers();
            LuaTable table = new LuaTable();
            for (int i = 0; i < list.size(); i++) {
                table.set(i + 1, CoerceJavaToLua.coerce(list.get(i)));
            }
            return table;
        }

        /**
         * Helper for publishFiles that takes a Lua table (coerced to LuaValue) or a single string
         */
        public boolean publishFiles(String publisherName, String topic, Object filePaths, long delayMs, boolean binary) {

            List<File> files = new ArrayList<>();
            if (filePaths instanceof LuaValue lv && lv.istable()) {
                int length = lv.length();
                for (int i = 1; i <= length; i++) {
                    Path validatedPath = zmqService.validatePath(lv.get(i).tojstring());
                    files.add(validatedPath.toFile());
                }
            } else if (filePaths != null) {
                Path validatedPath = zmqService.validatePath(filePaths.toString());
                files.add(validatedPath.toFile());
            }

            if (files.isEmpty()) {
                return false;
            }

            return zmqService.publishFiles(publisherName, topic, files, delayMs, binary);
        }

        /**
         * Alias for publishFiles to support older scripts
         */
        public boolean pubFiles(String publisherName, String topic, Object filePaths, long delayMs, boolean binary) {

            return publishFiles(publisherName, topic, filePaths, delayMs, binary);
        }

        public boolean publishFileList(String publisherName, String topic, String directory, LuaValue fileNames, long delayMs, boolean binary) {

            List<String> names = new ArrayList<>();
            if (fileNames.istable()) {
                int length = fileNames.length();
                for (int i = 1; i <= length; i++) {
                    names.add(fileNames.get(i).tojstring());
                }
            } else if (!fileNames.isnil()) {
                names.add(fileNames.tojstring());
            }

            if (names.isEmpty()) {
                return false;
            }

            return zmqService.publishFileList(publisherName, topic, directory, names, delayMs, binary);
        }

        public boolean pubFileList(String publisherName, String topic, String directory, LuaValue fileNames, long delayMs, boolean binary) {

            return publishFileList(publisherName, topic, directory, fileNames, delayMs, binary);
        }
    }
}
