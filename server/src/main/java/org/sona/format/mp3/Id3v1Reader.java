package org.sona.format.mp3;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
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
    private static final int TITLE = 3;
    private static final int ARTIST = 33;
    private static final int ALBUM = 63;
    // ID3v1.1 ends the comment early with a NUL, and puts the track number in its last byte.
    private static final int COMMENT_END = 125;
    private static final int TRACK = 126;

    /**
     * @return the tag's fields, or none when the channel doesn't end with a tag
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
        return Stream.of(
                        textField("TIT2", tag, TITLE),
                        textField("TPE1", tag, ARTIST),
                        textField("TALB", tag, ALBUM),
                        trackNumber(tag))
                .flatMap(Optional::stream)
                .toList();
    }

    // Fields are padded with NULs or, by some taggers, spaces.
    private static Optional<TextFrame> textField(final String frameId, final byte[] tag, final int offset) {
        var length = 0;
        while (length < FIELD_SIZE && tag[offset + length] != 0) {
            length++;
        }
        final var value = new String(tag, offset, length, StandardCharsets.ISO_8859_1).strip();
        if (value.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new TextFrame(frameId, List.of(value)));
    }

    private static Optional<TextFrame> trackNumber(final byte[] tag) {
        final var track = tag[TRACK] & 0xFF;
        if (tag[COMMENT_END] != 0 || track == 0) {
            return Optional.empty();
        }
        return Optional.of(new TextFrame("TRCK", List.of(Integer.toString(track))));
    }
}
