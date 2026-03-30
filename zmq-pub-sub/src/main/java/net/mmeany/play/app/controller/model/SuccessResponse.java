package net.mmeany.play.app.controller.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SuccessResponse {
    boolean success;
}
