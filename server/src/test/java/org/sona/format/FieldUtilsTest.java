package org.sona.format;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FieldUtilsTest {

    @Test
    void readsTextUpToFirstNul() {
        final var bytes = "xxAbc\0\0\0yy".getBytes(StandardCharsets.US_ASCII);

        assertThat(FieldUtils.nulPaddedString(bytes, 2, 6, StandardCharsets.US_ASCII))
                .isEqualTo("Abc");
    }

    @Test
    void readsTextThatFillsField() {
        final var bytes = "xxAbcdefyy".getBytes(StandardCharsets.US_ASCII);

        assertThat(FieldUtils.nulPaddedString(bytes, 2, 6, StandardCharsets.US_ASCII))
                .isEqualTo("Abcdef");
    }

    @Test
    void readsEmptyField() {
        assertThat(FieldUtils.nulPaddedString(new byte[4], 0, 4, StandardCharsets.ISO_8859_1))
                .isEmpty();
    }
}
