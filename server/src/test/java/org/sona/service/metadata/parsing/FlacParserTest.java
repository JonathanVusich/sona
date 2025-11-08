package org.sona.service.metadata.parsing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sona.model.exception.InvalidFormatException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class FlacParserTest {

    @ParameterizedTest
    @ValueSource(strings = {"sample-3.flac", "lucas_floyd_canyon.flac"})
    void parseFlacHeader(String file) throws IOException, InvalidFormatException {
        final var resource = FlacParserTest.class.getResourceAsStream(file);
        final var flacParser = new FlacParser();
        final var result = flacParser.parse(resource);

        assertThat(result).isNotNull();
    }
}