package org.sona.format.mp3;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class Id3UtilsTest {

    @Test
    void decodesSynchsafeInteger() {
        // Seven bits from each byte: 0x7F7F7F7F is the largest, 2^28 - 1.
        assertThat(Id3Utils.synchsafeInt(0x7F7F7F7F)).isEqualTo((1 << 28) - 1);
        assertThat(Id3Utils.synchsafeInt(0x00000219)).isEqualTo(0x119);
        assertThat(Id3Utils.synchsafeInt(0x00000101)).isEqualTo(0x81);
    }

    @Test
    void resynchroniseDropsTheZeroAfterEach0xFF() {
        final var unsynchronised = new byte[] {(byte) 0xFF, 0, (byte) 0xE0, 1, (byte) 0xFF, 0, 0, (byte) 0xFF};

        final var resynchronised = Id3Utils.resynchronise(ByteBuffer.wrap(unsynchronised));

        assertThat(Arrays.copyOf(resynchronised.array(), resynchronised.limit()))
                .containsExactly((byte) 0xFF, (byte) 0xE0, 1, (byte) 0xFF, 0, (byte) 0xFF);
    }

    @Test
    void decodesNulSeparatedStrings() {
        final var bytes = "\u0003Coldplay\0Guy Berryman\0".getBytes(StandardCharsets.UTF_8);

        assertThat(Id3Utils.decodeStrings(ByteBuffer.wrap(bytes), 1, TextEncoding.UTF_8))
                .containsExactly("Coldplay", "Guy Berryman");
    }

    @Test
    void decodesUtf16StringsWithTheirOwnByteOrderMarks() {
        final var little = "Ünïcode".getBytes(StandardCharsets.UTF_16LE);
        final var big = "Second".getBytes(StandardCharsets.UTF_16BE);
        final var bytes = ByteBuffer.allocate(2 + little.length + 2 + 2 + big.length)
                .put(new byte[] {(byte) 0xFF, (byte) 0xFE})
                .put(little)
                // The terminator, then the second string's byte order mark.
                .put(new byte[] {0, 0, (byte) 0xFE, (byte) 0xFF})
                .put(big)
                .array();

        assertThat(Id3Utils.decodeStrings(ByteBuffer.wrap(bytes), 0, TextEncoding.UTF_16))
                .containsExactly("Ünïcode", "Second");
    }

    @Test
    void utf16TerminatorFallsOnCharacterBoundary() {
        // "Ā" is 0x0100 and "A" is 0x0041: the zero bytes between them don't end the string.
        final var bytes = "ĀA".getBytes(StandardCharsets.UTF_16BE);

        assertThat(Id3Utils.decodeStrings(ByteBuffer.wrap(bytes), 0, TextEncoding.UTF_16BE))
                .containsExactly("ĀA");
    }
}
