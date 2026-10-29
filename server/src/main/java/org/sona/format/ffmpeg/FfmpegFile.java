package org.sona.format.ffmpeg;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.SeekableByteChannel;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;
import org.sona.exception.InvalidFormatException;
import org.sona.format.ParsedAudio;
import org.sona.format.PcmAudio;
import org.sona.metadata.RawMetadata;
import org.sona.metadata.RawMetadataBuilder;
import org.sona.metadata.Tag;
import org.sona.metadata.TagValue;

/**
 * A file opened by FFmpeg, whatever its format: its tags and duration as FFmpeg reads them, and its audio decoded by
 * FFmpeg. It holds native memory, so it has to be closed, and it reads the channel it's given until then.
 */
public final class FfmpegFile implements ParsedAudio, AutoCloseable {

    private static final int IO_BUFFER_SIZE = 32 * 1024;
    private static final long CONTEXT_SIZE = 256;
    private static final long STREAM_SIZE = 128;
    private static final int AV_DICT_IGNORE_SUFFIX = 2;
    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\s*(\\d+)");
    private static final int YEAR_LENGTH = 4;

    private final FfmpegLibrary ffmpeg;
    private final Arena arena = Arena.ofConfined();
    private final MemorySegment contextHolder = arena.allocate(ADDRESS);
    private final MemorySegment ioHolder = arena.allocate(ADDRESS);
    private RawMetadata metadata;
    private FfmpegAudio audio;

    /**
     * @throws InvalidFormatException when FFmpeg can't read the file as audio
     */
    public FfmpegFile(final FfmpegLibrary ffmpeg, final SeekableByteChannel channel) throws InvalidFormatException {
        this.ffmpeg = ffmpeg;
        try {
            open(channel);
            final var streamIndex = ffmpeg.callInt(
                    "av_find_best_stream", context(), FfmpegLibrary.AVMEDIA_TYPE_AUDIO, -1, -1, MemorySegment.NULL, 0);
            if (streamIndex < 0) {
                throw new InvalidFormatException("FFmpeg found no audio in the file");
            }
            final var stream = stream(streamIndex);
            this.metadata = readMetadata(stream);
            final var parameters = stream.get(ADDRESS, FfmpegLibrary.STREAM_CODECPAR);
            this.audio = new FfmpegAudio(ffmpeg, context(), streamIndex, parameters);
        } catch (InvalidFormatException | RuntimeException e) {
            close();
            throw e;
        }
    }

    @Override
    public RawMetadata metadata() {
        return metadata;
    }

    @Override
    public PcmAudio audio() {
        return audio;
    }

    private void open(final SeekableByteChannel channel) throws InvalidFormatException {
        final var io = new ChannelIo(channel);
        final var buffer = ffmpeg.callPointer("av_malloc", (long) IO_BUFFER_SIZE);
        final var avio = ffmpeg.callPointer(
                "avio_alloc_context",
                buffer,
                IO_BUFFER_SIZE,
                0,
                MemorySegment.NULL,
                io.readCallback(arena),
                MemorySegment.NULL,
                io.seekCallback(arena));
        ioHolder.set(ADDRESS, 0, avio);
        final var allocated = ffmpeg.callPointer("avformat_alloc_context");
        allocated.reinterpret(CONTEXT_SIZE).set(ADDRESS, FfmpegLibrary.CONTEXT_PB, avio);
        contextHolder.set(ADDRESS, 0, allocated);
        final var opened = ffmpeg.callInt(
                "avformat_open_input", contextHolder, arena.allocateFrom(""), MemorySegment.NULL, MemorySegment.NULL);
        if (opened < 0) {
            throw new InvalidFormatException("FFmpeg can't read the file (error %d)".formatted(opened));
        }
        if (ffmpeg.callInt("avformat_find_stream_info", context(), MemorySegment.NULL) < 0) {
            throw new InvalidFormatException("FFmpeg can't read the file's streams");
        }
    }

    private MemorySegment context() {
        return contextHolder.get(ADDRESS, 0).reinterpret(CONTEXT_SIZE);
    }

    private MemorySegment stream(final int index) {
        final var context = context();
        final var count = context.get(JAVA_INT, FfmpegLibrary.CONTEXT_NB_STREAMS);
        final var streams = context.get(ADDRESS, FfmpegLibrary.CONTEXT_STREAMS).reinterpret(8L * count);
        return streams.getAtIndex(ADDRESS, index).reinterpret(STREAM_SIZE);
    }

    private RawMetadata readMetadata(final MemorySegment stream) throws InvalidFormatException {
        final var context = context();
        final var micros = context.get(JAVA_LONG, FfmpegLibrary.CONTEXT_DURATION);
        if (micros == FfmpegLibrary.AV_NOPTS_VALUE) {
            throw new InvalidFormatException("FFmpeg can't tell how long the file is");
        }
        final var builder = new RawMetadataBuilder(Duration.ofNanos(micros * 1000));
        addTags(builder, context.get(ADDRESS, FfmpegLibrary.CONTEXT_METADATA));
        addTags(builder, stream.get(ADDRESS, FfmpegLibrary.STREAM_METADATA));
        return builder.build();
    }

    private void addTags(final RawMetadataBuilder builder, final MemorySegment dictionary) {
        final var empty = arena.allocateFrom("");
        var entry = ffmpeg.callPointer("av_dict_get", dictionary, empty, MemorySegment.NULL, AV_DICT_IGNORE_SUFFIX);
        while (!entry.equals(MemorySegment.NULL)) {
            final var fields = entry.reinterpret(16);
            final var key = text(fields.get(ADDRESS, FfmpegLibrary.ENTRY_KEY));
            addTag(builder, key, text(fields.get(ADDRESS, FfmpegLibrary.ENTRY_VALUE)));
            entry = ffmpeg.callPointer("av_dict_get", dictionary, empty, entry, AV_DICT_IGNORE_SUFFIX);
        }
    }

    private static String text(final MemorySegment string) {
        return string.reinterpret(Long.MAX_VALUE).getString(0);
    }

    // FFmpeg names tags as it likes whatever the format called them, so these are the names it gives for each Tag.
    private static void addTag(final RawMetadataBuilder builder, final String key, final String value) {
        switch (key.toLowerCase(Locale.ROOT)) {
            case "title" -> builder.addTag(Tag.TRACK_TITLE, new TagValue.Str(value));
            case "artist" -> builder.addTag(Tag.TRACK_ARTIST, new TagValue.Str(value));
            case "album_artist" -> builder.addTag(Tag.ALBUM_ARTIST, new TagValue.Str(value));
            case "album" -> builder.addTag(Tag.RELEASE_TITLE, new TagValue.Str(value));
            case "composer" -> builder.addTag(Tag.COMPOSER, new TagValue.Str(value));
            case "track" -> addNumber(builder, Tag.TRACK_NUMBER, key, value);
            case "disc" -> addNumber(builder, Tag.DISC_NUMBER, key, value);
            case "date" -> addYear(builder, key, value);
            default -> builder.addUnknownTag(key, value);
        }
    }

    // ID3 and MP4 write "3/12", where Vorbis comments write "3".
    private static void addNumber(
            final RawMetadataBuilder builder, final Tag tag, final String key, final String value) {
        final var matcher = LEADING_NUMBER.matcher(value);
        if (matcher.find()) {
            builder.addTag(tag, new TagValue.Int(Integer.parseInt(matcher.group(1))));
        } else {
            builder.addUnknownTag(key, value);
        }
    }

    private static void addYear(final RawMetadataBuilder builder, final String key, final String value) {
        if (value.length() >= YEAR_LENGTH
                && value.substring(0, YEAR_LENGTH).chars().allMatch(Character::isDigit)) {
            builder.addTag(Tag.RELEASE_YEAR, new TagValue.Int(Integer.parseInt(value.substring(0, YEAR_LENGTH))));
        } else {
            builder.addUnknownTag(key, value);
        }
    }

    @Override
    public void close() {
        if (audio != null) {
            audio.close();
        }
        ffmpeg.callVoid("avformat_close_input", contextHolder);
        final var avio = ioHolder.get(ADDRESS, 0);
        if (!avio.equals(MemorySegment.NULL)) {
            ffmpeg.callVoid("av_free", avio.reinterpret(16).get(ADDRESS, FfmpegLibrary.IO_BUFFER));
            ffmpeg.callVoid("avio_context_free", ioHolder);
        }
        arena.close();
    }
}
