package org.sona.format.mp3;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.sona.format.mp3.Id3Frame.TextFrame;
import org.sona.utils.ChannelUtils;

/**
 * Reads the ID3v1 tag that may end an MP3: 128 bytes of fixed-size fields, which older taggers write. Its fields are
 * returned as the ID3v2.3 frames that hold the same data, so both tags map to Sona's tags alike.
 */
final class Id3v1Reader {

    private static final int TAG_SIZE = 128;
    private static final byte[] IDENTIFIER = "TAG".getBytes(StandardCharsets.US_ASCII);
    private static final int FIELD_SIZE = 30;
    // ID3v1.1 ends the comment early with a NUL, and puts the track number in its last byte.
    private static final int COMMENT_END = 125;
    private static final int TRACK = 126;

    /**
     * @return the tag's fields that have a value, or none when the channel doesn't end with a tag
     */
    List<TextFrame> readTag(final SeekableByteChannel channel) throws IOException {
        final var size = channel.size();
        if (size < TAG_SIZE) {
            return List.of();
        }
        final var tag =
                ChannelUtils.readBytes(channel, size - TAG_SIZE, TAG_SIZE).array();
        if (!Arrays.equals(tag, 0, IDENTIFIER.length, IDENTIFIER, 0, IDENTIFIER.length)) {
            return List.of();
        }
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
        var length = 0;
        while (length < FIELD_SIZE && tag[offset + length] != 0) {
            length++;
        }
        return new String(tag, offset, length, StandardCharsets.ISO_8859_1).strip();
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
