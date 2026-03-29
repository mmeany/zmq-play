package net.mmeany.play.app.controller.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LuaExecutionResponse {
    private String result;
    private String error;
    private boolean success;
}
