package com.example.project_flow.controller;

import com.example.project_flow.exception.ConflictException;
import com.example.project_flow.exception.ForbiddenException;
import com.example.project_flow.exception.NotFoundException;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.Map;

/** Every API error body is {"message": "..."} and the message is safe to show to the user. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> conflict(ConflictException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> forbidden(ForbiddenException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> notFound(NotFoundException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalid(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .findFirst()
                .orElse("Check the form and try again.");
        return Map.of("message", message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> unreadable(Exception exception) {
        if (exception instanceof HttpMessageNotReadableException unreadable
                && unreadable.getCause() instanceof InvalidFormatException invalid && invalid.getTargetType().isEnum()
                && !invalid.getPath().isEmpty() && invalid.getPath().get(0).getPropertyName() != null) {
            return Map.of("message", "Choose a valid option for " + invalid.getPath().get(0).getPropertyName() + ".");
        }
        return Map.of("message", "Check the values you entered and try again.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> integrity() {
        return Map.of("message", "That change conflicts with an existing record. Refresh and try again.");
    }

    /** Any other database failure. The details go to the server log, not to the screen. */
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> database(DataAccessException exception) {
        LoggerFactory.getLogger(ApiExceptionHandler.class).error("Database error while handling a request", exception);
        return Map.of("message", "The database could not save this change. Try again. If it keeps happening, restart FOCUS FLOW.");
    }
}
