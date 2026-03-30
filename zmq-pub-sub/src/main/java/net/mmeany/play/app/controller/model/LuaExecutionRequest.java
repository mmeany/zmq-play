package net.mmeany.play.app.controller.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LuaExecutionRequest {
    private String script;
    private String fileName;
}
