package org.sona.format.mp3;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.sona.format.FieldUtils;
import org.sona.format.mp3.Id3Frame.TextFrame;

/**
 * Reads the ID3v1 tag that may end an MP3: 128 bytes of fixed-size fields, which older taggers write. Its fields are
 * returned as the ID3v2.3 frames that hold the same data, so both tags map to Sona's tags alike.
 */
final class Id3v1Reader {

    static final int TAG_SIZE = 128;
    private static final byte[] IDENTIFIER = "TAG".getBytes(StandardCharsets.US_ASCII);
    private static final int FIELD_SIZE = 30;
    // ID3v1.1 ends the comment early with a NUL, and puts the track number in its last byte.
    private static final int COMMENT_END = 125;
    private static final int TRACK = 126;

    /**
     * @return whether the channel ends with an ID3v1 tag
     */
    boolean hasTag(final SeekableByteChannel channel) throws IOException {
        final var size = channel.size();
        if (size < TAG_SIZE) {
            return false;
        }
        final var identifier = ByteBuffer.allocate(IDENTIFIER.length);
        IOUtils.readFully(channel.position(size - TAG_SIZE), identifier);
        return Arrays.equals(identifier.array(), IDENTIFIER);
    }

    /**
     * @return the fields that have a value, of the tag at the end of a channel that has one (see {@link #hasTag})
     */
    List<TextFrame> readTag(final SeekableByteChannel channel) throws IOException {
        final var buffer = ByteBuffer.allocate(TAG_SIZE);
        IOUtils.readFully(channel.position(channel.size() - TAG_SIZE), buffer);
        final var tag = buffer.array();
        final var frames = new ArrayList<TextFrame>();
        for (final var field : TextField.values()) {
            final var value = readTextField(tag, field.getOffset());
            if (!value.isEmpty()) {
                frames.add(new TextFrame(field.getFrameId(), List.of(value)));
            }
        }
        if (tag[COMMENT_END] == 0 && tag[TRACK] != 0) {
            frames.add(new TextFrame("TRCK", List.of(Integer.toString(tag[TRACK] & 0xFF))));
        }
        return List.copyOf(frames);
    }

    // Fields are padded with NULs or, by some taggers, spaces.
    private static String readTextField(final byte[] tag, final int offset) {
        return FieldUtils.nulPaddedString(tag, offset, FIELD_SIZE, StandardCharsets.ISO_8859_1)
                .strip();
    }

    @Getter
    @RequiredArgsConstructor
    private enum TextField {
        TITLE("TIT2", 3),
        ARTIST("TPE1", 33),
        ALBUM("TALB", 63);

        private final String frameId;
        private final int offset;
    }
}
