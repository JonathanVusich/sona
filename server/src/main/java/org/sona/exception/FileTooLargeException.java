package org.sona.exception;

import org.springframework.util.unit.DataSize;

/**
 * An upload larger than Sona accepts ({@code library.maxUploadSize}).
 */
public final class FileTooLargeException extends SonaException {

    public FileTooLargeException(final DataSize maxUploadSize) {
        super("The file is larger than %s".formatted(maxUploadSize));
    }
}
