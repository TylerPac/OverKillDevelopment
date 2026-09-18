package dev.tylerpac.backend.dayz.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import dev.tylerpac.backend.dayz.service.DayzApiException;

/** Scoped to the DayZ controllers only; the rest of the app keeps its per-controller error handling. */
@RestControllerAdvice(basePackageClasses = DayzExceptionHandler.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.dayz.enabled", havingValue = "true")
public class DayzExceptionHandler {

    @ExceptionHandler(DayzApiException.class)
    public ResponseEntity<String> handleApi(DayzApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidation(MethodArgumentNotValidException ex) {
        String code = "validation_failed";
        if (ex.getBindingResult().hasFieldErrors()) {
            String message = ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
            if (message != null) {
                code = message;
            }
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(code);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<String> handleBadBody(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("invalid_request_body");
    }
}
