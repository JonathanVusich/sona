package org.sona.format.mp3;

import dev.javax.bitstream.BitInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.sona.exception.InvalidFormatException;
import org.sona.format.mp3.Id3Frame.TextFrame;
import org.sona.format.mp3.Id3Frame.UniqueFileId;
import org.sona.format.mp3.Id3Frame.UserTextFrame;

/**
 * Reads the ID3v2 tag at the start of an MP3, in version 2.3 or 2.4 (id3.org's ID3v2.3.0 and ID3v2.4.0 structure
 * documents), straight from the file's stream through a bit stream. Every field is whole bytes, so the bit stream holds
 * no bits between fields, and the tag's regions skip what Sona doesn't read.
 */
final class Id3v2Reader {

    // "ID3"
    private static final long IDENTIFIER = 0x49_44_33;
    private static final int HEADER_SIZE = 10;
    private static final int FOOTER_SIZE = 10;
    private static final int FRAME_ID_SIZE = 4;
    // Frame IDs are capital letters and digits (ID3v2.4.0 structure section 4).
    private static final Pattern FRAME_ID = Pattern.compile("[A-Z0-9]{4}");

    private static final int EXTENDED_HEADER = 0x40;
    private static final int FOOTER = 0x10;

    /**
     * @param input the file's stream, at its start
     * @return the tag the file starts with, which is empty, with a size of 0, when it starts with none
     */
    Id3v2Tag readTag(final InputStream input) throws IOException, InvalidFormatException {
        final var bits = BitInputStream.wrap(input, ByteOrder.BIG_ENDIAN);
        if (bits.peekBits(24) != IDENTIFIER) {
            return new Id3v2Tag(0, List.of());
        }
        bits.readBits(24);
        final var version = Id3Version.fromMajorVersion((int) bits.readBits(8));
        // The revision changes nothing a reader needs.
        bits.readBits(8);
        final var flags = (int) bits.readBits(8);
        final var bodySize = Id3Utils.readSynchsafeInt(bits);
        // The bit stream has read all of the header, so the body follows in the stream.
        try {
            final var body = new Id3Region(input, bodySize, version.bodySynchronisation(flags));
            return new Id3v2Tag(tagSize(flags, bodySize), readFrames(version, flags, body));
        } catch (final EOFException cutShort) {
            throw new InvalidFormatException("ID3v2 tag is cut short!");
        }
    }

    private static long tagSize(final int flags, final int bodySize) {
        if ((flags & FOOTER) != 0) {
            return HEADER_SIZE + bodySize + FOOTER_SIZE;
        }
        return HEADER_SIZE + bodySize;
    }

    // Keeps the frames Sona reads, and skips the rest, as the spec lets a reader skip frames it doesn't know. Padding,
    // which must be all NULs, may follow the last frame, and no frame ID starts with a NUL.
    private static List<Id3Frame> readFrames(final Id3Version version, final int tagFlags, final Id3Region body)
            throws IOException, InvalidFormatException {
        final var bits = BitInputStream.wrap(body, ByteOrder.BIG_ENDIAN);
        skipExtendedHeader(version, tagFlags, body, bits);
        final var frames = new ArrayList<Id3Frame>();
        while (body.peek() > 0) {
            final var header = readFrameHeader(version, bits);
            if (isReadFrameId(header.frameId()) && !version.isEncoded(header.formatFlags())) {
                frames.add(readFrame(version, tagFlags, header, body));
            } else {
                body.skipNBytes(header.size());
            }
        }
        readPadding(body, bits);
        return List.copyOf(frames);
    }

    private static void skipExtendedHeader(
            final Id3Version version, final int tagFlags, final Id3Region body, final BitInputStream bits)
            throws IOException, InvalidFormatException {
        if ((tagFlags & EXTENDED_HEADER) == 0) {
            return;
        }
        final var size = version.readExtendedHeaderSize(bits);
        if (size < 0) {
            throw new InvalidFormatException("ID3v2 extended header is smaller than its size field!");
        }
        body.skipNBytes(size);
    }

    private static void readPadding(final Id3Region body, final BitInputStream bits)
            throws IOException, InvalidFormatException {
        while (body.hasMore()) {
            if (bits.readBits(8) != 0) {
                throw new InvalidFormatException("ID3v2 tag's padding isn't all NULs!");
            }
        }
    }

    private static Id3FrameHeader readFrameHeader(final Id3Version version, final BitInputStream bits)
            throws IOException, InvalidFormatException {
        final var frameId = readFrameId(bits);
        final var size = version.readFrameSize(bits);
        // The status flags come first, then the format flags.
        bits.readBits(8);
        final var formatFlags = (int) bits.readBits(8);
        if (size == 0) {
            throw new InvalidFormatException("ID3v2 frame %s is empty!".formatted(frameId));
        }
        return new Id3FrameHeader(frameId, size, formatFlags);
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

    private static boolean isReadFrameId(final String frameId) {
        return frameId.equals("UFID") || frameId.startsWith("T");
    }

    // The content follows the fields the frame's flags add.
    private static Id3Frame readFrame(
            final Id3Version version, final int tagFlags, final Id3FrameHeader header, final Id3Region body)
            throws IOException, InvalidFormatException {
        final var synchronisation = version.frameSynchronisation(tagFlags, header.formatFlags());
        final var frame = new Id3Region(body, header.size(), synchronisation);
        frame.skipNBytes(version.fieldsSize(header.formatFlags()));
        if (!frame.hasMore()) {
            throw new InvalidFormatException("ID3v2 frame %s has no content!".formatted(header.frameId()));
        }
        final var bits = BitInputStream.wrap(frame, ByteOrder.BIG_ENDIAN);
        return switch (header.frameId()) {
            case "TXXX" -> readUserText(frame, bits);
            case "UFID" -> readUniqueFileId(frame, bits);
            default -> new TextFrame(header.frameId(), readEncoding(bits).readStrings(frame, bits));
        };
    }

    // The first string names the text, and the rest are its values.
    private static UserTextFrame readUserText(final Id3Region frame, final BitInputStream bits)
            throws IOException, InvalidFormatException {
        final var strings = readEncoding(bits).readStrings(frame, bits);
        if (strings.isEmpty()) {
            throw new InvalidFormatException("ID3v2 TXXX frame has no description!");
        }
        return new UserTextFrame(strings.getFirst(), strings.subList(1, strings.size()));
    }

    // The owner is NUL-terminated ISO-8859-1. The identifier is binary, but MusicBrainz writes ASCII.
    private static UniqueFileId readUniqueFileId(final Id3Region frame, final BitInputStream bits)
            throws IOException, InvalidFormatException {
        final var strings = TextEncoding.ISO_8859_1.readStrings(frame, bits);
        if (strings.size() != 2) {
            throw new InvalidFormatException("ID3v2 UFID frame isn't an owner followed by an identifier!");
        }
        return new UniqueFileId(strings.getFirst(), strings.get(1));
    }

    // A text frame's first byte says how it encodes its text.
    private static TextEncoding readEncoding(final BitInputStream bits) throws IOException, InvalidFormatException {
        return TextEncoding.fromByte((int) bits.readBits(8));
    }

    private record Id3FrameHeader(String frameId, long size, int formatFlags) {}
}
