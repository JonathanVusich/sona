package org.sona.format.flac;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sona.exception.InvalidFormatException;
import org.sona.samples.FlacSample;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class FlacParserTest {

    @ParameterizedTest
    @EnumSource(value = FlacSample.class)
    void parseAllMetadataFlacHeader(FlacSample sample) throws IOException, InvalidFormatException {
        final var flacParser = new Flac();
        final var result = flacParser.parseAllMetadata(sample.inputStream());
        final var trackMetadata = flacParser.parse(sample.inputStream());

        assertThat(result).isNotNull();
        assertThat(trackMetadata).isNotNull();
    }
}