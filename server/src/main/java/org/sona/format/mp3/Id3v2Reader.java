package org.sona.format.mp3;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.sona.exception.InvalidFormatException;
import org.sona.format.mp3.Id3Frame.TextFrame;
import org.sona.format.mp3.Id3Frame.UniqueFileId;
import org.sona.format.mp3.Id3Frame.UserTextFrame;
import org.sona.utils.ChannelUtils;

/**
 * Reads the ID3v2 tag at the start of an MP3, in version 2.3 or 2.4 (id3.org's ID3v2.3.0 and ID3v2.4.0 structure
 * documents).
 */
final class Id3v2Reader {

    private static final byte[] IDENTIFIER = "ID3".getBytes(StandardCharsets.US_ASCII);
    private static final int HEADER_SIZE = 10;
    private static final int FOOTER_SIZE = 10;
    private static final int FRAME_HEADER_SIZE = 10;
    private static final int FRAME_ID_SIZE = 4;

    private static final int EXTENDED_HEADER = 0x40;
    private static final int FOOTER = 0x10;

    /**
     * @return the tag at the start of the channel, or empty when it has none
     */
    Optional<Id3v2Tag> readTag(final SeekableByteChannel channel) throws IOException, InvalidFormatException {
        if (channel.size() < HEADER_SIZE) {
            return Optional.empty();
        }
        final var header = ChannelUtils.readBytes(channel, 0, HEADER_SIZE);
        if (!Arrays.equals(header.array(), 0, IDENTIFIER.length, IDENTIFIER, 0, IDENTIFIER.length)) {
            return Optional.empty();
        }
        final var majorVersion = header.get(3);
        final var flags = header.get(5) & 0xFF;
        final var bodySize = Id3Utils.synchsafeInt(header.getInt(6));
        final var body = ChannelUtils.readBytes(channel, HEADER_SIZE, bodySize).array();
        return Optional.of(new Id3v2Tag(tagSize(flags, bodySize), readFrames(majorVersion, flags, body)));
    }

    private static long tagSize(final int flags, final int bodySize) {
        if ((flags & FOOTER) != 0) {
            return HEADER_SIZE + bodySize + FOOTER_SIZE;
        }
        return HEADER_SIZE + bodySize;
    }

    private static List<Id3Frame> readFrames(final int majorVersion, final int flags, final byte[] body)
            throws InvalidFormatException {
        return switch (majorVersion) {
            case 3 -> readFrames(Id3Version.V2_3, flags, v23Body(flags, body));
            case 4 -> readFrames(Id3Version.V2_4, flags, ByteBuffer.wrap(body));
            // TODO: Read ID3v2.2 tags, whose frame IDs have three letters.
            // Another version may be laid out differently, so the spec says to skip its tag.
            default -> List.of();
        };
    }

    // ID3v2.3 unsynchronises the whole tag after its header, frame headers included.
    private static ByteBuffer v23Body(final int flags, final byte[] body) {
        if ((flags & Id3Version.UNSYNCHRONISED_TAG) != 0) {
            return ByteBuffer.wrap(Id3Utils.resynchronise(body));
        }
        return ByteBuffer.wrap(body);
    }

    private static List<Id3Frame> readFrames(final Id3Version version, final int tagFlags, final ByteBuffer body)
            throws InvalidFormatException {
        skipExtendedHeader(version, tagFlags, body);
        final var frames = new ArrayList<Id3Frame>();
        // Padding, all NULs, may follow the last frame.
        while (body.remaining() >= FRAME_HEADER_SIZE && body.get(body.position()) != 0) {
            readNextFrame(version, tagFlags, body).ifPresent(frames::add);
        }
        return frames;
    }

    private static void skipExtendedHeader(final Id3Version version, final int tagFlags, final ByteBuffer body) {
        if ((tagFlags & EXTENDED_HEADER) == 0 || body.remaining() < Integer.BYTES) {
            return;
        }
        final var size = version.extendedHeaderSize(body.getInt(body.position()));
        body.position(Math.min(body.position() + size, body.limit()));
    }

    /**
     * @return the frame at the body's position, or empty when Sona skips its kind or can't read it
     */
    private static Optional<Id3Frame> readNextFrame(final Id3Version version, final int tagFlags, final ByteBuffer body)
            throws InvalidFormatException {
        final var frameId = new String(readBytes(body, FRAME_ID_SIZE), StandardCharsets.ISO_8859_1);
        final var size = version.frameSize(body.getInt());
        final var formatFlags = body.get(body.position() + 1) & 0xFF;
        body.position(body.position() + 2);
        if (size < 0 || size > body.remaining()) {
            // A frame can't run past the tag, so the rest can't be read either.
            body.position(body.limit());
            return Optional.empty();
        }
        final var data = readBytes(body, size);
        final var content = version.frameContent(tagFlags, formatFlags, data).orElse(null);
        if (content == null || content.length == 0) {
            return Optional.empty();
        }
        return readFrameContent(frameId, content);
    }

    private static Optional<Id3Frame> readFrameContent(final String frameId, final byte[] content)
            throws InvalidFormatException {
        return switch (frameId) {
            case "TXXX" -> readUserText(content);
            case "UFID" -> readUniqueFileId(content);
            default -> readText(frameId, content);
        };
    }

    private static Optional<Id3Frame> readText(final String frameId, final byte[] content)
            throws InvalidFormatException {
        if (!frameId.startsWith("T")) {
            return Optional.empty();
        }
        final var encoding = TextEncoding.fromByte(content[0] & 0xFF);
        return Optional.of(new TextFrame(frameId, Id3Utils.decodeStrings(content, 1, encoding)));
    }

    // The first string names the text, and the rest are its values.
    private static Optional<Id3Frame> readUserText(final byte[] content) throws InvalidFormatException {
        final var encoding = TextEncoding.fromByte(content[0] & 0xFF);
        final var strings = Id3Utils.decodeStrings(content, 1, encoding);
        if (strings.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new UserTextFrame(strings.getFirst(), List.copyOf(strings.subList(1, strings.size()))));
    }

    // The owner is NUL-terminated ISO-8859-1. The identifier is binary, but MusicBrainz writes ASCII.
    private static Optional<Id3Frame> readUniqueFileId(final byte[] content) {
        final var strings = Id3Utils.decodeStrings(content, 0, TextEncoding.ISO_8859_1);
        if (strings.size() < 2) {
            return Optional.empty();
        }
        return Optional.of(new UniqueFileId(strings.getFirst(), strings.get(1)));
    }

    private static byte[] readBytes(final ByteBuffer buffer, final int length) {
        final var bytes = new byte[length];
        buffer.get(bytes);
        return bytes;
    }
}
