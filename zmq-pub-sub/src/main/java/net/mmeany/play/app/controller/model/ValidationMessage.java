package net.mmeany.play.app.controller.model;

import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

@Builder
@Jacksonized
public record ValidationMessage(
        String parameter,
        String reason
) {}
