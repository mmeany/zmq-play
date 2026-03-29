package net.mmeany.play.app.controller.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

import java.util.List;


@Builder
@Jacksonized
public record ValidationFailedResponse(
        @Schema(title = "Error code", example = "VALIDATION_ERROR")
        String errorCode,
        @Schema(title = "Error message(s)")
        List<ValidationMessage> validationMessages
) {}
