package org.sona.format.mp3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.javax.bitstream.BitInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.sona.exception.InvalidFormatException;

class Id3UtilsTest {

    @Test
    void readsSynchsafeInteger() throws IOException, InvalidFormatException {
        // Seven bits from each byte: 7F7F7F7F is the largest, 2^28 - 1.
        assertThat(Id3Utils.readSynchsafeInt(bits("7F7F7F7F"))).isEqualTo((1 << 28) - 1);
        assertThat(Id3Utils.readSynchsafeInt(bits("00000219"))).isEqualTo(0x119);
        assertThat(Id3Utils.readSynchsafeInt(bits("00000101"))).isEqualTo(0x81);
    }

    @Test
    void rejectsSynchsafeIntegerWithTopBitSet() {
        assertThatThrownBy(() -> Id3Utils.readSynchsafeInt(bits("00000080")))
                .isInstanceOf(InvalidFormatException.class);
    }

    @Test
    void resynchroniseDropsTheZeroAfterEach0xFF() {
        final var unsynchronised = new byte[] {9, (byte) 0xFF, 0, (byte) 0xE0, 1, (byte) 0xFF, 0, 0, (byte) 0xFF, 9};

        assertThat(Id3Utils.resynchronise(unsynchronised, 1, 9))
                .containsExactly((byte) 0xFF, (byte) 0xE0, 1, (byte) 0xFF, 0, (byte) 0xFF);
    }

    @Test
    void decodesNulSeparatedStrings() {
        final var bytes = "\u0003Coldplay\0Guy Berryman\0".getBytes(StandardCharsets.UTF_8);

        assertThat(Id3Utils.decodeStrings(bytes, 1, bytes.length, TextEncoding.UTF_8))
                .containsExactly("Coldplay", "Guy Berryman");
    }

    @Test
    void decodesUtf16StringsWithTheirOwnByteOrderMarks() {
        final var bytes = new ByteArrayOutputStream();
        bytes.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xFE});
        bytes.writeBytes("Ünïcode".getBytes(StandardCharsets.UTF_16LE));
        // The terminator, then the second string's byte order mark.
        bytes.writeBytes(new byte[] {0, 0, (byte) 0xFE, (byte) 0xFF});
        bytes.writeBytes("Second".getBytes(StandardCharsets.UTF_16BE));
        final var array = bytes.toByteArray();

        assertThat(Id3Utils.decodeStrings(array, 0, array.length, TextEncoding.UTF_16))
                .containsExactly("Ünïcode", "Second");
    }

    @Test
    void utf16TerminatorFallsOnCharacterBoundary() {
        // "Ā" is 0x0100 and "A" is 0x0041: the zero bytes between them don't end the string.
        final var bytes = "ĀA".getBytes(StandardCharsets.UTF_16BE);

        assertThat(Id3Utils.decodeStrings(bytes, 0, bytes.length, TextEncoding.UTF_16BE))
                .containsExactly("ĀA");
    }

    private static BitInputStream bits(final String hex) {
        return BitInputStream.wrap(new ByteArrayInputStream(HexFormat.of().parseHex(hex)), ByteOrder.BIG_ENDIAN);
    }

    @Test
    void decodesOnlyTheRange() {
        final var bytes = "xxOnexx".getBytes(StandardCharsets.US_ASCII);

        assertThat(Id3Utils.decodeStrings(bytes, 2, 5, TextEncoding.ISO_8859_1)).containsExactly("One");
    }
}
