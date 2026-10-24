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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipException;
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

    // "ID3" starts a tag's header, and "3DI" its footer.
    private static final long IDENTIFIER = 0x49_44_33;
    private static final long FOOTER_IDENTIFIER = 0x33_44_49;
    private static final int HEADER_SIZE = 10;
    private static final int FOOTER_SIZE = 10;
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
            final var appendedTag = readAppendedTag(channel, channel.size() - id3v1Tag.size());
            final var id3v2Tag = readId3v2Tag(streamAt(channel, 0));
            final var audioStart = id3v2Tag.size();
            final var audioEnd = channel.size() - id3v1Tag.size() - appendedTag.size();
            final var streamInfo = streamInfoReader.readStreamInfo(streamAt(channel, audioStart), audioStart, audioEnd);
            channel.position(streamInfo.audioStart());
            final var frames = mergeFrames(List.of(appendedTag.frames(), id3v2Tag.frames(), id3v1Tag.frames()));
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

    /**
     * Reads the ID3v2.4 tag that may follow the audio, whose footer ends it. An encoder that updates a file's tags
     * without rewriting the file appends one.
     *
     * @param end where the tag would end: the start of the ID3v1 tag, or the end of the file without one
     * @return the tag, which is empty, with a size of 0, when there's none
     */
    private Id3v2Tag readAppendedTag(final SeekableByteChannel channel, final long end)
            throws IOException, InvalidFormatException {
        final var footerStart = end - FOOTER_SIZE;
        if (footerStart < 0) {
            return new Id3v2Tag(0, List.of());
        }
        final var bits = BitInputStream.wrap(streamAt(channel, footerStart), ByteOrder.BIG_ENDIAN);
        if (bits.readBits(24) != FOOTER_IDENTIFIER) {
            return new Id3v2Tag(0, List.of());
        }
        // The footer repeats the header's version, revision and flags, then its body's size.
        bits.readBits(24);
        final var tagStart = footerStart - Id3Utils.readSynchsafeInt(bits) - HEADER_SIZE;
        return readFooterTag(channel, tagStart, end);
    }

    // The tag the footer gives the size of must start where the footer says, and end at the footer.
    private Id3v2Tag readFooterTag(final SeekableByteChannel channel, final long tagStart, final long end)
            throws IOException, InvalidFormatException {
        if (tagStart < 0) {
            throw new InvalidFormatException("ID3v2 footer gives a tag longer than the file!");
        }
        final var tag = readId3v2Tag(streamAt(channel, tagStart));
        if (tag.size() != end - tagStart) {
            throw new InvalidFormatException("ID3v2 footer doesn't end the tag it gives the size of!");
        }
        return tag;
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
            return new Id3v2Tag(tagSize(flags, bodySize), readBody(version, flags, body));
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

    // A compressed ID3v2.2 tag is skipped, as its spec says.
    private static List<Id3Frame> readBody(final Id3Version version, final int tagFlags, final Id3Region body)
            throws IOException, InvalidFormatException {
        if (version.isCompressedTag(tagFlags)) {
            body.skip(Long.MAX_VALUE);
            return List.of();
        }
        return readFrames(version, tagFlags, body);
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
            if (isReadFrameId(header.frameId()) && !version.isEncrypted(header.formatFlags())) {
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
        final var size = version.readExtendedHeaderSize(bits, tagFlags);
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
        final var header = version.readFrameHeader(bits);
        if (header.size() == 0) {
            throw new InvalidFormatException("ID3v2 frame %s is empty!".formatted(header.frameId()));
        }
        return header;
    }

    private static boolean isReadFrameId(final String frameId) {
        return frameId.equals("UFID") || frameId.startsWith("T");
    }

    // The data follows the fields the frame's flags add. Compressed data may end before the frame does, so the rest
    // is skipped.
    private static Id3Frame readFrame(
            final Id3Version version, final int tagFlags, final Id3FrameHeader header, final Id3Region body)
            throws IOException, InvalidFormatException {
        final var synchronisation = version.frameSynchronisation(tagFlags, header.formatFlags());
        final var frame = new Id3Region(body, header.size(), synchronisation);
        final var inflatedSize =
                version.readFrameFields(BitInputStream.wrap(frame, ByteOrder.BIG_ENDIAN), header.formatFlags());
        final var id3Frame = readFrameData(version, header, frame, inflatedSize);
        frame.skip(Long.MAX_VALUE);
        return id3Frame;
    }

    private static Id3Frame readFrameData(
            final Id3Version version, final Id3FrameHeader header, final Id3Region frame, final long inflatedSize)
            throws IOException, InvalidFormatException {
        if (!version.isCompressed(header.formatFlags())) {
            return readFrameContent(header.frameId(), frame);
        }
        if (inflatedSize == 0) {
            throw new InvalidFormatException(
                    "ID3v2 frame %s is compressed without its size!".formatted(header.frameId()));
        }
        return readCompressedFrame(header.frameId(), frame, inflatedSize);
    }

    // Compressed data is zlib's, and the content is read as it's inflated.
    private static Id3Frame readCompressedFrame(final String frameId, final Id3Region frame, final long inflatedSize)
            throws IOException, InvalidFormatException {
        try (final var inflated = new InflaterInputStream(frame)) {
            return readFrameContent(frameId, new Id3Region(inflated, inflatedSize, Synchronisation.SYNCHRONISED));
        } catch (final ZipException notZlib) {
            throw new InvalidFormatException("ID3v2 frame %s isn't valid zlib data!".formatted(frameId));
        }
    }

    private static Id3Frame readFrameContent(final String frameId, final Id3Region content)
            throws IOException, InvalidFormatException {
        if (!content.hasMore()) {
            throw new InvalidFormatException("ID3v2 frame %s has no content!".formatted(frameId));
        }
        final var bits = BitInputStream.wrap(content, ByteOrder.BIG_ENDIAN);
        return switch (frameId) {
            case "TXXX" -> readUserText(content, bits);
            case "UFID" -> readUniqueFileId(content, bits);
            default -> new TextFrame(frameId, readEncoding(bits).readStrings(content, bits));
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
    // An appended tag updates the tag before the audio. A tag may hold several frames of one kind, so a tag's frames
    // only replace another's whole kinds.
    private static List<Id3Frame> mergeFrames(final List<List<? extends Id3Frame>> tagsByPrecedence) {
        final var frames = new ArrayList<Id3Frame>();
        final var mergedKeys = new HashSet<String>();
        for (final var tag : tagsByPrecedence) {
            final var tagKeys = tag.stream().map(Mp3Parser::frameKey).collect(Collectors.toUnmodifiableSet());
            tag.stream().filter(frame -> !mergedKeys.contains(frameKey(frame))).forEach(frames::add);
            mergedKeys.addAll(tagKeys);
        }
        return List.copyOf(frames);
    }

    // What a frame holds: a text frame's ID, a TXXX frame's description, or a UFID frame's owner.
    private static String frameKey(final Id3Frame frame) {
        return switch (frame) {
            case TextFrame text -> text.frameId();
            case UserTextFrame userText -> "TXXX:" + userText.description();
            case UniqueFileId uniqueFileId -> "UFID:" + uniqueFileId.owner();
        };
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
}
