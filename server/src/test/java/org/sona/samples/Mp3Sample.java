package org.sona.samples;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;

/**
 * One-second 440 Hz tones that FFmpeg 4.2 encoded with LAME and tagged, and files derived from them for what no tool
 * at hand writes. The derived ones were written by a one-off script, to the byte layouts in the ID3v2 and MPEG audio
 * specs, and FFmpeg reads back the tags and frames it can (ID3v2.2, and ID3v2.4's compressed and unsynchronised
 * frames).
 */
public enum Mp3Sample {
    // 44.1 kHz joint stereo at 128 kbit/s, with an Info header and LAME tag, and an ID3v2.4 tag.
    STEREO_CBR_ID3V24("stereo_cbr_id3v24.mp3"),
    // 22.05 kHz (MPEG-2) mono at a variable bitrate, with a Xing header and LAME tag, an ID3v2.3 tag and an ID3v1 tag.
    MONO_VBR_ID3V23("mono_vbr_id3v23.mp3"),
    // 44.1 kHz joint stereo at 128 kbit/s, with no VBR header and only an ID3v1 tag. FFmpeg only writes ID3v1 tags
    // with ID3v2 ones, so the ID3v2 tag was cut from the start.
    NO_XING_ID3V1("no_xing_id3v1.mp3"),
    // NO_XING_ID3V1 with the bitrate index of every frame header cleared, so its frames are free format.
    FREE_FORMAT("free_format.mp3"),
    // The first five frames of NO_XING_ID3V1 after an ID3v2.2 tag, which has a UTF-16 artist, TXX and UFI frames.
    ID3V22("id3v22.mp3"),
    // The same frames after an ID3v2.3 tag whose TIT2 frame is compressed.
    COMPRESSED_ID3V23("compressed_id3v23.mp3"),
    // The same frames after an ID3v2.4 tag whose TIT2 frame is compressed, and whose TPE1 frame is unsynchronised.
    COMPRESSED_ID3V24("compressed_id3v24.mp3"),
    // The same frames between an ID3v2.4 tag with a title and an artist, and an appended ID3v2.4 tag with a title,
    // followed by an ID3v1 tag with a title and an album.
    APPENDED_ID3V24("appended_id3v24.mp3");

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
