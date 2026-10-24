package org.sona.format.mp3;

import dev.javax.bitstream.BitInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.commons.io.IOUtils;
import org.sona.exception.InvalidFormatException;
import org.sona.format.mp3.Id3Frame.TextFrame;
import org.sona.format.mp3.Id3Frame.UniqueFileId;
import org.sona.format.mp3.Id3Frame.UserTextFrame;

/**
 * Reads the ID3v2 tag at the start of an MP3, in version 2.3 or 2.4 (id3.org's ID3v2.3.0 and ID3v2.4.0 structure
 * documents). The tag's body is read once: its headers' fields are read through a bit stream, and its frames' text
 * from where it sits in the body.
 */
final class Id3v2Reader {

    static final int HEADER_SIZE = 10;

    private static final byte[] IDENTIFIER = "ID3".getBytes(StandardCharsets.US_ASCII);
    private static final int FOOTER_SIZE = 10;
    private static final int FRAME_HEADER_SIZE = 10;
    private static final int FRAME_ID_SIZE = 4;
    // Frame IDs are capital letters and digits (ID3v2.4.0 structure section 4).
    private static final Pattern FRAME_ID = Pattern.compile("[A-Z0-9]{4}");

    private static final int EXTENDED_HEADER = 0x40;
    private static final int FOOTER = 0x10;

    /**
     * @param header the first {@link #HEADER_SIZE} bytes of the file
     * @return whether they're the header of an ID3v2 tag
     */
    boolean isTagHeader(final byte[] header) {
        return Arrays.equals(header, 0, IDENTIFIER.length, IDENTIFIER, 0, IDENTIFIER.length);
    }

    /**
     * Reads the tag's body, which follows its header.
     *
     * @param header the tag's header (see {@link #isTagHeader})
     */
    Id3v2Tag readTag(final SeekableByteChannel channel, final byte[] header)
            throws IOException, InvalidFormatException {
        final var fields = new ByteArrayInputStream(header, IDENTIFIER.length, HEADER_SIZE - IDENTIFIER.length);
        final var bits = BitInputStream.wrap(fields, ByteOrder.BIG_ENDIAN);
        final var majorVersion = (int) bits.readBits(8);
        // The revision changes nothing a reader needs.
        bits.readBits(8);
        final var flags = (int) bits.readBits(8);
        final var bodySize = Id3Utils.readSynchsafeInt(bits);
        final var input = Channels.newInputStream(channel.position(HEADER_SIZE));
        final var body = IOUtils.readFully(input, bodySize);
        return new Id3v2Tag(tagSize(flags, bodySize), readFrames(majorVersion, flags, body));
    }

    private static long tagSize(final int flags, final int bodySize) {
        if ((flags & FOOTER) != 0) {
            return HEADER_SIZE + bodySize + FOOTER_SIZE;
        }
        return HEADER_SIZE + bodySize;
    }

    private static List<Id3Frame> readFrames(final int majorVersion, final int flags, final byte[] body)
            throws IOException, InvalidFormatException {
        return switch (majorVersion) {
            case 3 -> readFrames(Id3Version.V2_3, flags, v23Body(flags, body));
            case 4 -> readFrames(Id3Version.V2_4, flags, body);
            // TODO: Read ID3v2.2 tags, whose frame IDs have three letters.
            default -> throw new InvalidFormatException("ID3v2.%d tags aren't supported!".formatted(majorVersion));
        };
    }

    // ID3v2.3 unsynchronises the whole tag after its header, frame headers included.
    private static byte[] v23Body(final int flags, final byte[] body) {
        if ((flags & Id3Version.UNSYNCHRONISED_TAG) != 0) {
            return Id3Utils.resynchronise(body, 0, body.length);
        }
        return body;
    }

    // Keeps the frames Sona reads, and skips the rest, as the spec lets a reader skip frames it doesn't know. The bit
    // stream takes only the bytes each read needs, so the body's stream says where each frame's data starts.
    private static List<Id3Frame> readFrames(final Id3Version version, final int tagFlags, final byte[] body)
            throws IOException, InvalidFormatException {
        final var input = new ByteArrayInputStream(body);
        final var bits = BitInputStream.wrap(input, ByteOrder.BIG_ENDIAN);
        skipExtendedHeader(version, tagFlags, input, bits);
        final var frames = new ArrayList<Id3Frame>();
        while (hasNextFrame(body, input)) {
            final var header = readFrameHeader(version, input, bits);
            final var dataStart = body.length - input.available();
            input.skipNBytes(header.size());
            if (isReadFrameId(header.frameId()) && !version.isEncoded(header.formatFlags())) {
                frames.add(readFrameData(version, tagFlags, header, body, dataStart));
            }
        }
        return frames;
    }

    private static void skipExtendedHeader(
            final Id3Version version, final int tagFlags, final ByteArrayInputStream input, final BitInputStream bits)
            throws IOException, InvalidFormatException {
        if ((tagFlags & EXTENDED_HEADER) == 0) {
            return;
        }
        if (input.available() < Integer.BYTES) {
            throw new InvalidFormatException("ID3v2 tag ends in its extended header!");
        }
        final var size = version.readExtendedHeaderSize(bits);
        if (size < 0 || size > input.available()) {
            throw new InvalidFormatException("ID3v2 extended header runs past the end of its tag!");
        }
        input.skipNBytes(size);
    }

    // Padding, which must be all NULs, may follow the last frame.
    private static boolean hasNextFrame(final byte[] body, final ByteArrayInputStream input)
            throws InvalidFormatException {
        final var offset = body.length - input.available();
        if (offset == body.length || body[offset] != 0) {
            return offset < body.length;
        }
        for (var i = offset; i < body.length; i++) {
            if (body[i] != 0) {
                throw new InvalidFormatException("ID3v2 tag's padding isn't all NULs!");
            }
        }
        return false;
    }

    private static Id3FrameHeader readFrameHeader(
            final Id3Version version, final ByteArrayInputStream input, final BitInputStream bits)
            throws IOException, InvalidFormatException {
        if (input.available() < FRAME_HEADER_SIZE) {
            throw new InvalidFormatException("ID3v2 tag ends in a frame header!");
        }
        final var frameId = readFrameId(bits);
        final var size = version.readFrameSize(bits);
        // The status flags come first, then the format flags.
        bits.readBits(8);
        final var formatFlags = (int) bits.readBits(8);
        if (size <= 0 || size > input.available()) {
            throw new InvalidFormatException(
                    "ID3v2 frame %s has a size of %d, which doesn't fit its tag!".formatted(frameId, size));
        }
        return new Id3FrameHeader(frameId, (int) size, formatFlags);
    }

    private static String readFrameId(final BitInputStream bits) throws IOException, InvalidFormatException {
        final var frameId = new StringBuilder(FRAME_ID_SIZE);
        for (var i = 0; i < FRAME_ID_SIZE; i++) {
            frameId.append((char) bits.readBits(8));
        }
        if (!FRAME_ID.matcher(frameId).matches()) {
            throw new InvalidFormatException("ID3v2 frame ID '%s' isn't valid!".formatted(frameId));
        }
        return frameId.toString();
    }

    // The content follows the fields the frame's flags add. Only resynchronising it takes a copy.
    private static Id3Frame readFrameData(
            final Id3Version version,
            final int tagFlags,
            final Id3FrameHeader header,
            final byte[] body,
            final int dataStart)
            throws InvalidFormatException {
        final var contentStart = dataStart + version.fieldsSize(header.formatFlags());
        final var contentEnd = dataStart + header.size();
        if (contentStart >= contentEnd) {
            throw new InvalidFormatException("ID3v2 frame %s has no content!".formatted(header.frameId()));
        }
        if (version.isUnsynchronisedFrame(tagFlags, header.formatFlags())) {
            final var content = Id3Utils.resynchronise(body, contentStart, contentEnd);
            return readFrame(header.frameId(), content, 0, content.length);
        }
        return readFrame(header.frameId(), body, contentStart, contentEnd);
    }

    private static boolean isReadFrameId(final String frameId) {
        return frameId.equals("UFID") || frameId.startsWith("T");
    }

    private static Id3Frame readFrame(final String frameId, final byte[] bytes, final int start, final int end)
            throws InvalidFormatException {
        return switch (frameId) {
            case "TXXX" -> readUserText(bytes, start, end);
            case "UFID" -> readUniqueFileId(bytes, start, end);
            default ->
                new TextFrame(frameId, Id3Utils.decodeStrings(bytes, start + 1, end, readEncoding(bytes, start)));
        };
    }

    // The first string names the text, and the rest are its values.
    private static UserTextFrame readUserText(final byte[] bytes, final int start, final int end)
            throws InvalidFormatException {
        final var strings = Id3Utils.decodeStrings(bytes, start + 1, end, readEncoding(bytes, start));
        if (strings.isEmpty()) {
            throw new InvalidFormatException("ID3v2 TXXX frame has no description!");
        }
        return new UserTextFrame(strings.getFirst(), List.copyOf(strings.subList(1, strings.size())));
    }

    // The owner is NUL-terminated ISO-8859-1. The identifier is binary, but MusicBrainz writes ASCII.
    private static UniqueFileId readUniqueFileId(final byte[] bytes, final int start, final int end)
            throws InvalidFormatException {
        final var strings = Id3Utils.decodeStrings(bytes, start, end, TextEncoding.ISO_8859_1);
        if (strings.size() != 2) {
            throw new InvalidFormatException("ID3v2 UFID frame isn't an owner followed by an identifier!");
        }
        return new UniqueFileId(strings.getFirst(), strings.get(1));
    }

    // A text frame's first byte says how it encodes its text.
    private static TextEncoding readEncoding(final byte[] bytes, final int start) throws InvalidFormatException {
        return TextEncoding.fromByte(bytes[start] & 0xFF);
    }

    private record Id3FrameHeader(String frameId, int size, int formatFlags) {}
}
