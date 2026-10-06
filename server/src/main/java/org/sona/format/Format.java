package org.sona.format;

import lombok.Getter;

import java.util.List;

@Getter
public enum Format {
    FLAC("flac");

    private final List<String> extensions;

    Format(final String... extensions) {
        this.extensions = List.of(extensions);
    }
}
