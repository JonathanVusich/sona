package org.sona.samples;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;

/**
 * MP3s that ingestion tests upload. sonaudio's own tests cover how they parse and decode.
 */
public enum Mp3Sample {
    // A one-second 440 Hz tone that FFmpeg 4.2 encoded with LAME: 44.1 kHz joint stereo at 128 kbit/s, with an Info
    // header and LAME tag, and an ID3v2.4 tag.
    STEREO_CBR_ID3V24("stereo_cbr_id3v24.mp3"),
    // The ISO/IEC 11172-4 conformance stream of MPEG-1 mono at 32 kHz, going through every bitrate from 32 to 320
    // kbit/s, as FFmpeg's test suite has it (fate-suite.ffmpeg.org/mp3-conformance).
    HE_32KHZ("he_32khz.mp3");

    private final String fileName;

    Mp3Sample(final String fileName) {
        this.fileName = fileName;
    }

    public InputStream inputStream() {
        return Mp3Sample.class.getResourceAsStream("mp3/" + fileName);
    }

    /**
     * @return the file in memory, as parsers read it
     */
    public SeekableByteChannel channel() throws IOException {
        try (final var inputStream = inputStream()) {
            return new SeekableInMemoryByteChannel(inputStream.readAllBytes());
        }
    }
}
