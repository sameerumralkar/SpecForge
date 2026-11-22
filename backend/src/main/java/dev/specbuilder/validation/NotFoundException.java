package dev.specbuilder.validation;

import java.util.UUID;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String what, UUID id) {
        super(what + " " + id + " was not found");
    }

    public NotFoundException(String message) {
        super(message);
    }
}
