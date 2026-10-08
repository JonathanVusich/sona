package org.sona.library;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PathSegmentTest {

    private static final UUID ID = UUID.fromString("120c786d-a3b2-3c19-b4ff-2b7b3b4435bf");

    @Test
    void formatAndParseRoundTrip() {
        final var segment = new PathSegment(ID, "A Rush of Blood to the Head");

        assertThat(segment.toString()).isEqualTo("120c786d-a3b2-3c19-b4ff-2b7b3b4435bf[A Rush of Blood to the Head]");
        assertThat(PathSegment.parse(segment.toString())).isEqualTo(segment);
    }

    @Test
    void parseNameContainingBrackets() {
        final var segment = new PathSegment(ID, "Live [Remastered]");

        assertThat(PathSegment.parse(segment.toString())).isEqualTo(segment);
    }

    @ParameterizedTest
    @CsvSource({
            "AC/DC, AC_DC",
            "'What?: Live', What__ Live",
            "'../../etc', .._.._etc",
    })
    void sanitizeUnsafeNames(String name, String expected) {
        assertThat(new PathSegment(ID, name).name()).isEqualTo(expected);
    }
}
