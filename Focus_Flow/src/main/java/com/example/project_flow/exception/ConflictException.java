package com.example.project_flow.exception;

/** The request is well formed but not allowed in the current state. Message is shown to the user. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
