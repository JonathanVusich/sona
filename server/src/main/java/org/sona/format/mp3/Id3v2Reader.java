package org.sona.format.mp3;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
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
    // Frame IDs are capital letters and digits (ID3v2.4.0 structure section 4).
    private static final Pattern FRAME_ID = Pattern.compile("[A-Z0-9]{4}");

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
            default -> throw new InvalidFormatException("ID3v2.%d tags aren't supported!".formatted(majorVersion));
        };
    }

    // ID3v2.3 unsynchronises the whole tag after its header, frame headers included.
    private static ByteBuffer v23Body(final int flags, final byte[] body) {
        if ((flags & Id3Version.UNSYNCHRONISED_TAG) != 0) {
            return ByteBuffer.wrap(Id3Utils.resynchronise(body));
        }
        return ByteBuffer.wrap(body);
    }

    // Keeps the frames Sona reads, and skips the rest, as the spec lets a reader skip frames it doesn't know.
    private static List<Id3Frame> readFrames(final Id3Version version, final int tagFlags, final ByteBuffer body)
            throws InvalidFormatException {
        skipExtendedHeader(version, tagFlags, body);
        final var frames = new ArrayList<Id3Frame>();
        while (hasNextFrame(body)) {
            final var header = readFrameHeader(version, body);
            final var data = readBytes(body, header.size());
            if (isReadFrameId(header.frameId()) && !version.isEncoded(header.formatFlags())) {
                frames.add(readFrame(header.frameId(), version.frameContent(tagFlags, header.formatFlags(), data)));
            }
        }
        return frames;
    }

    private static void skipExtendedHeader(final Id3Version version, final int tagFlags, final ByteBuffer body)
            throws InvalidFormatException {
        if ((tagFlags & EXTENDED_HEADER) == 0) {
            return;
        }
        if (body.remaining() < Integer.BYTES) {
            throw new InvalidFormatException("ID3v2 tag ends in its extended header!");
        }
        final var size = version.extendedHeaderSize(body.getInt(body.position()));
        if (size < 0 || size > body.remaining()) {
            throw new InvalidFormatException("ID3v2 extended header runs past the end of its tag!");
        }
        body.position(body.position() + size);
    }

    // Padding, which must be all NULs, may follow the last frame.
    private static boolean hasNextFrame(final ByteBuffer body) throws InvalidFormatException {
        if (!body.hasRemaining()) {
            return false;
        }
        if (body.get(body.position()) != 0) {
            return true;
        }
        while (body.hasRemaining()) {
            if (body.get() != 0) {
                throw new InvalidFormatException("ID3v2 tag's padding isn't all NULs!");
            }
        }
        return false;
    }

    private static Id3FrameHeader readFrameHeader(final Id3Version version, final ByteBuffer body)
            throws InvalidFormatException {
        if (body.remaining() < FRAME_HEADER_SIZE) {
            throw new InvalidFormatException("ID3v2 tag ends in a frame header!");
        }
        final var frameId = new String(readBytes(body, FRAME_ID_SIZE), StandardCharsets.ISO_8859_1);
        if (!FRAME_ID.matcher(frameId).matches()) {
            throw new InvalidFormatException("ID3v2 frame ID '%s' isn't valid!".formatted(frameId));
        }
        final var size = version.frameSize(body.getInt());
        // The status flags come first, then the format flags.
        final var formatFlags = body.get(body.position() + 1) & 0xFF;
        body.position(body.position() + 2);
        if (size <= 0 || size > body.remaining()) {
            throw new InvalidFormatException(
                    "ID3v2 frame %s has a size of %d, which doesn't fit its tag!".formatted(frameId, size));
        }
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
            throw new InvalidFormatException("ID3v2 TXXX frame has no description!");
        }
        return new UserTextFrame(strings.getFirst(), List.copyOf(strings.subList(1, strings.size())));
    }

    // The owner is NUL-terminated ISO-8859-1. The identifier is binary, but MusicBrainz writes ASCII.
    private static UniqueFileId readUniqueFileId(final byte[] content) throws InvalidFormatException {
        final var strings = Id3Utils.decodeStrings(content, 0, TextEncoding.ISO_8859_1);
        if (strings.size() != 2) {
            throw new InvalidFormatException("ID3v2 UFID frame isn't an owner followed by an identifier!");
        }
        return new UniqueFileId(strings.getFirst(), strings.get(1));
    }

    private static byte[] readBytes(final ByteBuffer buffer, final int length) {
        final var bytes = new byte[length];
        buffer.get(bytes);
        return bytes;
    }

    private record Id3FrameHeader(String frameId, int size, int formatFlags) {}
}
