package org.sona.format;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import org.sona.model.enums.AudioFormat;

@Getter
public enum Format {
    FLAC("flac");

    private final List<String> extensions;

    Format(final String... extensions) {
        this.extensions = List.of(extensions);
    }

    /**
     * @return the extensions of every format Sona can ingest, lowercase and without the dot
     */
    public static List<String> supportedExtensions() {
        return Arrays.stream(values())
                .flatMap(format -> format.getExtensions().stream())
                .toList();
    }

    /**
     * @return the format as the database stores it
     */
    public AudioFormat toAudioFormat() {
        return switch (this) {
            case FLAC -> AudioFormat.FLAC;
        };
    }
}
