package com.example.project_flow.admin.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.util.Map;

/**
 * Upload errors in the same {"message": "..."} shape as ApiExceptionHandler. Only multipart
 * exceptions are handled here, and only the admin upload endpoints accept multipart requests.
 */
@RestControllerAdvice
public class AdminExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.CONTENT_TOO_LARGE)
    public Map<String, String> tooLarge() {
        return Map.of("message", "A file is too large. Each file can be up to "
                + AdminWebConfig.MAX_FILE_BYTES / (1024 * 1024) + " MB.");
    }

    @ExceptionHandler(MultipartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badUpload() {
        return Map.of("message", "The upload could not be read. Try again.");
    }
}
