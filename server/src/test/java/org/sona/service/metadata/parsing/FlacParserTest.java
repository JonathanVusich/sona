package org.sona.service.metadata.parsing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sona.exception.InvalidFormatException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class FlacParserTest {

    @ParameterizedTest
    @ValueSource(strings = {"sample-3.flac", "lucas_floyd_canyon.flac"})
    void parseAllMetadataFlacHeader(String file) throws IOException, InvalidFormatException {
        final var flacParser = new FlacParser();
        final var result = flacParser.parseAllMetadata(FlacParserTest.class.getResourceAsStream(file));
        final var trackMetadata = flacParser.parse(FlacParserTest.class.getResourceAsStream(file));

        assertThat(result).isNotNull();
        assertThat(trackMetadata).isNotNull();
    }
}