package com.example.project_flow.integration.ownersoffice;

/** A failed call to Owner's Office. The message is safe to show: it never contains the token. */
public class OwnersOfficeException extends RuntimeException {

    public OwnersOfficeException(String message) {
        super(message);
    }
}
