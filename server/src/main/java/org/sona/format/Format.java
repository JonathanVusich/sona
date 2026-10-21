package org.sona.format;

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
     * @return the format as the database stores it
     */
    public AudioFormat toAudioFormat() {
        return switch (this) {
            case FLAC -> AudioFormat.FLAC;
        };
    }
}
