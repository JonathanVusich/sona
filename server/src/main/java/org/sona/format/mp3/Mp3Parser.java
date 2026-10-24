package org.sona.format.mp3;

import dev.javax.bitstream.BitInputStream;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sona.exception.InvalidFormatException;
import org.sona.format.Format;
import org.sona.format.Parser;
import org.sona.format.mp3.Id3Frame.TextFrame;
import org.sona.format.mp3.Id3Frame.UniqueFileId;
import org.sona.format.mp3.Id3Frame.UserTextFrame;
import org.sona.metadata.RawMetadata;

/**
 * Reads an MP3 straight from the file's stream through bit streams: its ID3v2 tag at its start, in version 2.3 or 2.4
 * (id3.org's ID3v2.3.0 and ID3v2.4.0 structure documents), the ID3v1 tag that may end it, and its length from its first
 * frame. Every field of the ID3v2 tag is whole bytes, so the bit stream holds no bits between fields, and the tag's
 * regions skip what Sona doesn't read.
 */
public final class Mp3Parser implements Parser {

    // "ID3"
    private static final long IDENTIFIER = 0x49_44_33;
    private static final int HEADER_SIZE = 10;
    private static final int FOOTER_SIZE = 10;
    private static final int FRAME_ID_SIZE = 4;
    // Frame IDs are capital letters and digits (ID3v2.4.0 structure section 4).
    private static final Pattern FRAME_ID = Pattern.compile("[A-Z0-9]{4}");

    private static final int EXTENDED_HEADER = 0x40;
    private static final int FOOTER = 0x10;

    // Picard's TXXX descriptions, and the names it gives the same tags in Vorbis comments, so unknown tags have the
    // same names whether they were read from an MP3 or a FLAC file.
    private static final Map<String, String> PICARD_USER_TEXT = Map.of(
            "MusicBrainz Artist Id", "musicbrainz_artistid",
            "MusicBrainz Album Id", "musicbrainz_albumid",
            "MusicBrainz Album Artist Id", "musicbrainz_albumartistid",
            "MusicBrainz Release Group Id", "musicbrainz_releasegroupid",
            "MusicBrainz Release Track Id", "musicbrainz_releasetrackid",
            "MusicBrainz Work Id", "musicbrainz_workid",
            "MusicBrainz Disc Id", "musicbrainz_discid",
            "MusicBrainz Album Type", "releasetype",
            "MusicBrainz Album Status", "releasestatus",
            "MusicBrainz Album Release Country", "releasecountry");
    // Picard keeps the recording's MBID in a UFID frame of this owner, and calls it MUSICBRAINZ_TRACKID in FLAC.
    private static final String MUSICBRAINZ_OWNER = "http://musicbrainz.org";
    private static final String MUSICBRAINZ_RECORDING_ID = "musicbrainz_trackid";

    private final Id3v1Reader id3v1Reader = new Id3v1Reader();
    private final StreamInfoReader streamInfoReader = new StreamInfoReader();

    @Override
    public Format format() {
        return Format.MP3;
    }

    // The tags are read first, as they say where the audio starts and ends.
    @Override
    public ParsedMp3 parse(final SeekableByteChannel channel) throws IOException, InvalidFormatException {
        try {
            final var id3v1Tag = readId3v1Tag(channel);
            final var id3v2Tag = readId3v2Tag(streamAt(channel, 0));
            final var audioStart = id3v2Tag.size();
            final var audioEnd = channel.size() - id3v1Tag.size();
            final var streamInfo = streamInfoReader.readStreamInfo(streamAt(channel, audioStart), audioStart, audioEnd);
            channel.position(streamInfo.audioStart());
            final var frames = mergeFrames(id3v2Tag.frames(), id3v1Tag.frames());
            return new ParsedMp3(rawMetadata(streamInfo.duration(), frames), new Mp3Decoder(streamInfo));
        } catch (final EOFException cutShort) {
            throw new InvalidFormatException("MP3 is cut short!");
        }
    }

    // A file shorter than the tag can't end with one.
    private Id3v1Tag readId3v1Tag(final SeekableByteChannel channel) throws IOException {
        final var tagStart = channel.size() - Id3v1Reader.TAG_SIZE;
        if (tagStart < 0) {
            return new Id3v1Tag(0, List.of());
        }
        return id3v1Reader.readTag(streamAt(channel, tagStart));
    }

    // Buffered, as the readers' bit streams take a few bytes at a time.
    private static InputStream streamAt(final SeekableByteChannel channel, final long position) throws IOException {
        return new BufferedInputStream(Channels.newInputStream(channel.position(position)));
    }

    /**
     * @param input the file's stream, at its start
     * @return the tag the file starts with, which is empty, with a size of 0, when it starts with none
     */
    Id3v2Tag readId3v2Tag(final InputStream input) throws IOException, InvalidFormatException {
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

    // ID3v1's fields are cut-down copies of ID3v2's where a file has both, so they only fill in what ID3v2 lacks.
    private static List<Id3Frame> mergeFrames(final List<Id3Frame> id3v2Frames, final List<TextFrame> id3v1Frames) {
        final var id3v2FrameIds = id3v2Frames.stream()
                .flatMap(frame -> switch (frame) {
                    case TextFrame text -> Stream.of(text.frameId());
                    case UserTextFrame _, UniqueFileId _ -> Stream.<String>empty();
                })
                .collect(Collectors.toUnmodifiableSet());
        final var missingFrames = id3v1Frames.stream().filter(frame -> !id3v2FrameIds.contains(frame.frameId()));
        return Stream.concat(id3v2Frames.stream(), missingFrames).toList();
    }

    private static RawMetadata rawMetadata(final Duration duration, final List<Id3Frame> frames) {
        final var rawMetadata = new RawMetadata(duration);
        for (final var frame : frames) {
            final var id3Tag = id3Tag(frame);
            final var values = frameValues(frame);
            for (final var value : values) {
                if (id3Tag != null) {
                    rawMetadata.addTag(id3Tag.getTag(), id3Tag.toTagValue(value));
                } else {
                    rawMetadata.addUnknownTag(unknownTagName(frame), value);
                }
            }
        }
        return rawMetadata;
    }

    // Null for a frame that holds none of Sona's tags.
    private static Id3Tag id3Tag(final Id3Frame frame) {
        return switch (frame) {
            case TextFrame text -> Id3Tag.fromFrameId(text.frameId());
            case UserTextFrame _, UniqueFileId _ -> null;
        };
    }

    private static List<String> frameValues(final Id3Frame frame) {
        return switch (frame) {
            case TextFrame text -> text.values();
            case UserTextFrame userText -> userText.values();
            case UniqueFileId uniqueFileId -> List.of(uniqueFileId.identifier());
        };
    }

    private static String unknownTagName(final Id3Frame frame) {
        return switch (frame) {
            case TextFrame text -> text.frameId().toLowerCase(Locale.ROOT);
            case UserTextFrame userText ->
                PICARD_USER_TEXT.getOrDefault(
                        userText.description(), userText.description().toLowerCase(Locale.ROOT));
            case UniqueFileId uniqueFileId -> uniqueFileIdName(uniqueFileId.owner());
        };
    }

    private static String uniqueFileIdName(final String owner) {
        if (owner.equals(MUSICBRAINZ_OWNER)) {
            return MUSICBRAINZ_RECORDING_ID;
        }
        return "ufid:" + owner.toLowerCase(Locale.ROOT);
    }

    private record Id3FrameHeader(String frameId, long size, int formatFlags) {}
}
