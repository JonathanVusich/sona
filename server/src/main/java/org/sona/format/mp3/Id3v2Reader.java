package org.sona.format.mp3;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
     * @return whether the channel starts with an ID3v2 tag
     */
    boolean hasTag(final SeekableByteChannel channel) throws IOException {
        if (channel.size() < HEADER_SIZE) {
            return false;
        }
        final var identifier =
                ChannelUtils.readBytes(channel, 0, IDENTIFIER.length).array();
        return Arrays.equals(identifier, IDENTIFIER);
    }

    /**
     * @return the tag at the start of a channel that has one (see {@link #hasTag})
     */
    Id3v2Tag readTag(final SeekableByteChannel channel) throws IOException, InvalidFormatException {
        final var header = ChannelUtils.readBytes(channel, 0, HEADER_SIZE);
        final var majorVersion = header.get(3);
        final var flags = header.get(5) & 0xFF;
        final var bodySize = Id3Utils.synchsafeInt(header.getInt(6));
        final var body = ChannelUtils.readBytes(channel, HEADER_SIZE, bodySize).array();
        return new Id3v2Tag(tagSize(flags, bodySize), readFrames(majorVersion, flags, body));
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

    // Keeps the frames Sona reads, and skips the rest.
    private static List<Id3Frame> readFrames(final Id3Version version, final int tagFlags, final ByteBuffer body)
            throws InvalidFormatException {
        skipExtendedHeader(version, tagFlags, body);
        final var frames = new ArrayList<Id3Frame>();
        while (hasNextFrame(body)) {
            final var header = readFrameHeader(version, body);
            if (header.size() < 0 || header.size() > body.remaining()) {
                // A frame can't run past the tag, so the rest can't be read either.
                break;
            }
            final var data = readBytes(body, header.size());
            if (isReadFrameId(header.frameId()) && version.isReadable(header.formatFlags(), data.length)) {
                frames.add(readFrame(header.frameId(), version.frameContent(tagFlags, header.formatFlags(), data)));
            }
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

    // Padding, all NULs, may follow the last frame.
    private static boolean hasNextFrame(final ByteBuffer body) {
        return body.remaining() >= FRAME_HEADER_SIZE && body.get(body.position()) != 0;
    }

    private static Id3FrameHeader readFrameHeader(final Id3Version version, final ByteBuffer body) {
        final var frameId = new String(readBytes(body, FRAME_ID_SIZE), StandardCharsets.ISO_8859_1);
        final var size = version.frameSize(body.getInt());
        // The status flags come first, then the format flags.
        final var formatFlags = body.get(body.position() + 1) & 0xFF;
        body.position(body.position() + 2);
        return new Id3FrameHeader(frameId, size, formatFlags);
    }

    private static boolean isReadFrameId(final String frameId) {
        return frameId.equals("UFID") || frameId.startsWith("T");
    }

    private static Id3Frame readFrame(final String frameId, final byte[] content) throws InvalidFormatException {
        return switch (frameId) {
            case "TXXX" -> readUserText(content);
            case "UFID" -> readUniqueFileId(content);
            default ->
                new TextFrame(frameId, Id3Utils.decodeStrings(content, 1, TextEncoding.fromByte(content[0] & 0xFF)));
        };
    }

    // The first string names the text, and the rest are its values.
    private static UserTextFrame readUserText(final byte[] content) throws InvalidFormatException {
        final var strings = Id3Utils.decodeStrings(content, 1, TextEncoding.fromByte(content[0] & 0xFF));
        if (strings.isEmpty()) {
            return new UserTextFrame("", List.of());
        }
        return new UserTextFrame(strings.getFirst(), List.copyOf(strings.subList(1, strings.size())));
    }

    // The owner is NUL-terminated ISO-8859-1. The identifier is binary, but MusicBrainz writes ASCII. A frame cut off
    // after its owner has an empty identifier.
    private static UniqueFileId readUniqueFileId(final byte[] content) {
        final var strings = Id3Utils.decodeStrings(content, 0, TextEncoding.ISO_8859_1);
        return new UniqueFileId(strings.getFirst(), String.join("", strings.subList(1, strings.size())));
    }

    private static byte[] readBytes(final ByteBuffer buffer, final int length) {
        final var bytes = new byte[length];
        buffer.get(bytes);
        return bytes;
    }

    private record Id3FrameHeader(String frameId, int size, int formatFlags) {}
}
