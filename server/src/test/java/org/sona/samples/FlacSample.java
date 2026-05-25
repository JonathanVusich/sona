package org.sona.samples;

import java.io.InputStream;

public enum FlacSample {
    SAMPLE_3("sample-3.flac"),
    COLDPLAY("coldplay_a_whisper.flac"),
    LUCAS_FLOYD("lucas_floyd_canyon.flac");

    private final String fileName;

    FlacSample(final String fileName) {
        this.fileName = fileName;
    }

    public String fileName() {
        return fileName;
    }

    public InputStream inputStream() {
        return FlacSample.class.getResourceAsStream(fileName);
    }
}
