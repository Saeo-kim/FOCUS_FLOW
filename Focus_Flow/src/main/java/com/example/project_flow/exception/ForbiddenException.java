package com.example.project_flow.exception;

/** The signed-in person may see the page but may not perform this action. Message is shown to the user. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
