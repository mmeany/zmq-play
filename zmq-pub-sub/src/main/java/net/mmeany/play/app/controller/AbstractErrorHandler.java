package net.mmeany.play.app.controller;

import jakarta.validation.ConstraintViolationException;
import net.mmeany.play.app.controller.model.ValidationFailedResponse;
import net.mmeany.play.app.controller.model.ValidationMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;


/**
 * To use this class, extend it and annotate with '@ControllerAdvice'.
 * <p>
 * Additional handlers can be added to the implementation as required.
 */
public abstract class AbstractErrorHandler {

    protected static final String VALIDATION_ERROR = "VALIDATION_ERROR";

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ValidationFailedResponse> handleIllegalArguments(IllegalArgumentException exception) {

        List<ValidationMessage> validationErrors = List.of(ValidationMessage.builder()
                                                                            .parameter("ARGUMENT")
                                                                            .reason(exception.getMessage())
                                                                            .build());

        return badRequest(validationErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ValidationFailedResponse> handleJsonError(HttpMessageNotReadableException exception) {

        List<ValidationMessage> validationErrors = List.of(ValidationMessage.builder()
                                                                            .parameter("JSON")
                                                                            .reason(exception.getMessage())
                                                                            .build());

        return badRequest(validationErrors);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ValidationFailedResponse> handleMissingArguments(BindException exception) {

        List<ValidationMessage> validationErrors = exception.getBindingResult().getAllErrors()
                                                            .stream()
                                                            .map(e -> new ValidationMessage(getParameterName(e), e.getDefaultMessage()))
                                                            .toList();

        return badRequest(validationErrors);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ValidationFailedResponse> handleMissingArguments(MethodArgumentNotValidException exception) {

        List<ValidationMessage> validationErrors = exception.getBindingResult().getAllErrors()
                                                            .stream()
                                                            .map(e -> new ValidationMessage(getParameterName(e), e.getDefaultMessage()))
                                                            .toList();

        return badRequest(validationErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ValidationFailedResponse> handleConstraintViolationException(ConstraintViolationException e) {

        List<ValidationMessage> errors = e.getConstraintViolations()
                                          .stream()
                                          .map(cv -> new ValidationMessage(cv.getPropertyPath().toString(), cv.getMessage()))
                                          .toList();
        return badRequest(errors);
    }

    protected String getParameterName(final ObjectError error) {

        if (error instanceof FieldError fieldError) {
            return fieldError.getField();
        }
        return error.getObjectName();
    }

    protected ResponseEntity<ValidationFailedResponse> badRequest(List<ValidationMessage> errors) {

        ValidationFailedResponse errorResponse = new ValidationFailedResponse(VALIDATION_ERROR, errors);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
}
