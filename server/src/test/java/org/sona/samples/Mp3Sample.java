package org.sona.samples;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;

/**
 * One-second 440 Hz tones that FFmpeg 4.2 encoded with LAME and tagged.
 */
public enum Mp3Sample {
    // 44.1 kHz joint stereo at 128 kbit/s, with an Info header and LAME tag, and an ID3v2.4 tag.
    STEREO_CBR_ID3V24("stereo_cbr_id3v24.mp3"),
    // 22.05 kHz (MPEG-2) mono at a variable bitrate, with a Xing header and LAME tag, an ID3v2.3 tag and an ID3v1 tag.
    MONO_VBR_ID3V23("mono_vbr_id3v23.mp3"),
    // 44.1 kHz joint stereo at 128 kbit/s, with no VBR header and only an ID3v1 tag. FFmpeg only writes ID3v1 tags
    // with ID3v2 ones, so the ID3v2 tag was cut from the start.
    NO_XING_ID3V1("no_xing_id3v1.mp3");

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
