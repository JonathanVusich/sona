package org.sona.exception;

import java.io.Serial;

public final class InvalidFormatException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidFormatException(String message) {
        super(message);
    }
}
