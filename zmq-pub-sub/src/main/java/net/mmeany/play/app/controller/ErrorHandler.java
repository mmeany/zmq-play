package net.mmeany.play.app.controller;

import net.mmeany.play.app.controller.model.ErrorResponse;
import net.mmeany.play.app.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class ErrorHandler extends AbstractErrorHandler {

    @ExceptionHandler(AppException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleGameDetailsException(AppException exception) {

        return new ResponseEntity<>(ErrorResponse.builder()
                                                 .message(exception.getMessage())
                                                 .build(), HttpStatus.BAD_REQUEST);
    }
}
