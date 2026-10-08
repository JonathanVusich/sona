package org.sona.format.flac;

import org.junit.jupiter.api.Test;
import org.sona.exception.InvalidFormatException;
import org.sona.metadata.Blob;
import org.sona.metadata.RawMetadata;
import org.sona.metadata.Tag;
import org.sona.metadata.TagValue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlacWriterTest {

    @Test
    void writtenMetadataParsesBackUnchanged() throws IOException, InvalidFormatException {
        final var metadata = new RawMetadata(Duration.ofSeconds(57, 160_000_000));
        metadata.add(Tag.TRACK_TITLE, new TagValue.Str("Canyon"));
        metadata.add(Tag.ALBUM_ARTIST, new TagValue.Str("Philip Glass"));
        metadata.add(Tag.ALBUM_ARTIST, new TagValue.Str("Paul Barnes"));
        metadata.add(Tag.TRACK_NUMBER, new TagValue.Int(8));
        metadata.add(Tag.RELEASE_YEAR, new TagValue.Int(2002));
        metadata.add("musicbrainz_trackid", "1b3c1a6e-5d2b-4d0e-9a52-6ad0a6a0e1f2");

        final var parsed = new FlacParser().parse(new ByteArrayInputStream(write(metadata)));

        assertThat(parsed.duration()).isEqualTo(metadata.duration());
        for (final var tag : Tag.values()) {
            assertThat(parsed.search(tag)).as(tag.name()).containsExactlyElementsOf(metadata.search(tag));
        }
        assertThat(parsed.unknownTags()).containsExactlyInAnyOrderElementsOf(metadata.unknownTags().toList());
    }

    @Test
    void rejectBinaryTags() {
        final var metadata = new RawMetadata(Duration.ofSeconds(1));
        metadata.add(Tag.TRACK_TITLE, new TagValue.Binary(Blob.of(new byte[]{1})));

        assertThatThrownBy(() -> write(metadata)).isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] write(final RawMetadata metadata) throws IOException {
        final var outputStream = new ByteArrayOutputStream();
        new FlacWriter().write(metadata, outputStream);
        return outputStream.toByteArray();
    }
}
