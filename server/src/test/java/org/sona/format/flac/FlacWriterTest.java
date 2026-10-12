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
        metadata.addTag(Tag.TRACK_TITLE, new TagValue.Str("Canyon"));
        metadata.addTag(Tag.ALBUM_ARTIST, new TagValue.Str("Philip Glass"));
        metadata.addTag(Tag.ALBUM_ARTIST, new TagValue.Str("Paul Barnes"));
        metadata.addTag(Tag.TRACK_NUMBER, new TagValue.Int(8));
        metadata.addTag(Tag.RELEASE_YEAR, new TagValue.Int(2002));
        metadata.addUnknownTag("musicbrainz_trackid", "1b3c1a6e-5d2b-4d0e-9a52-6ad0a6a0e1f2");

        final var parsed = new FlacParser().parseMetadata(new ByteArrayInputStream(writeFlac(metadata)));

        assertThat(parsed.duration()).isEqualTo(metadata.duration());
        for (final var tag : Tag.values()) {
            assertThat(parsed.findValues(tag)).as(tag.name()).containsExactlyElementsOf(metadata.findValues(tag));
        }
        assertThat(parsed.unknownTags()).containsExactlyInAnyOrderElementsOf(metadata.unknownTags().toList());
    }

    @Test
    void rejectBinaryTags() {
        final var metadata = new RawMetadata(Duration.ofSeconds(1));
        metadata.addTag(Tag.TRACK_TITLE, new TagValue.Binary(Blob.copyOf(new byte[]{1})));

        assertThatThrownBy(() -> writeFlac(metadata)).isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] writeFlac(final RawMetadata metadata) throws IOException {
        final var outputStream = new ByteArrayOutputStream();
        new FlacWriter().writeMetadata(metadata, outputStream);
        return outputStream.toByteArray();
    }
}
