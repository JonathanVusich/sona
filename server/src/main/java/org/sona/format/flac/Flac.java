package org.sona.format.flac;

import org.sona.exception.InvalidFormatException;
import org.sona.format.Format;
import org.sona.format.ParsedAudio;
import org.sona.format.Parser;
import org.sona.format.Writer;
import org.sona.metadata.RawMetadata;
import org.sona.metadata.TagValue;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

import static java.util.function.Predicate.not;

public final class Flac implements Parser, Writer {

    static final byte[] FLAC_HEADER = "fLaC".getBytes(StandardCharsets.US_ASCII);

    @Override
    public Format format() {
        return Format.FLAC;
    }

    @Override
    public ParsedAudio parse(final InputStream inputStream) throws IOException, InvalidFormatException {
        final var flacMetadata = new FlacParser(inputStream).parseAllMetadata();
        final var streamInfo = flacMetadata.metadataBlocks().getOrDefault(BlockType.STREAM_INFO, List.of()).stream()
                .map(StreamInfo.class::cast)
                .findFirst()
                .orElseThrow(() -> new InvalidFormatException("FLAC file is missing its STREAMINFO block!"));
        final var commentBlocks = flacMetadata.metadataBlocks().getOrDefault(BlockType.VORBIS_COMMENT, List.of());
        final var comments = commentBlocks.stream()
                .filter(VorbisComment.class::isInstance)
                .map(VorbisComment.class::cast)
                .toList();

        final var rawMetadata = new RawMetadata();

        for (final var comment : comments) {
            for (final var field : comment.fields()) {

                final var fieldName = field.name().toLowerCase();
                final var flacTag = FlacTag.from(fieldName);
                if (flacTag != null) {
                    rawMetadata.add(flacTag.getTag(), flacTag.convert(field.content()));
                } else {
                    rawMetadata.add(fieldName, field.content());
                }
            }
        }

        return new ParsedAudio(duration(streamInfo), rawMetadata);
    }

    @Override
    public void write(final RawMetadata rawMetadata, final OutputStream outputStream) throws IOException {
        final var flacWriter = new FlacWriter(outputStream);

        // Write FLAC header
        flacWriter.writeFlacHeader();

        final var flacTags = Arrays.stream(FlacTag.values())
                .map(FlacTag::getTag)
                .map(tag -> retrieve(rawMetadata, tag))
                .filter(not(Objects::isNull))
                .toList();

        final var rawTags = rawMetadata.unknownTags()
                .toList();
    }

    private TagValue retrieve(RawMetadata metadata, org.sona.metadata.Tag tag) {
        return metadata.search(tag).stream().findFirst().orElse(null);
    }

    private static Duration duration(final StreamInfo streamInfo) {
        final var samples = streamInfo.interChannelSamples();
        final var sampleRate = streamInfo.sampleRate();
        return Duration.ofSeconds(samples / sampleRate, (samples % sampleRate) * 1_000_000_000L / sampleRate);
    }

}
