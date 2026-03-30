package net.mmeany.play.app.service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LuaHelper {

    public void sleep(Object milliseconds) {

        try {
            log.info("Lua script sleeping for {}ms", milliseconds);
            long ms = milliseconds instanceof Number number
                    ? number.longValue()
                    : Long.parseLong(milliseconds.toString());
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            log.error("Lua sleep interrupted", e);
            Thread.currentThread().interrupt();
        }
    }
}
