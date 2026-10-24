package org.sona.format.mp3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.javax.bitstream.BitInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sona.exception.InvalidFormatException;

class FrameHeaderTest {

    @Test
    void readsMpeg1FrameHeader() throws IOException, InvalidFormatException {
        // The first frame of audio in Mp3Sample.STEREO_CBR_ID3V24.
        final var header = readHeader("FFFB9064");

        assertThat(header).isEqualTo(new FrameHeader(MpegVersion.MPEG_1, 128_000, 44100, 0, ChannelMode.JOINT_STEREO));
        assertThat(header.frameLength()).isEqualTo(417);
        assertThat(header.channelCount()).isEqualTo(2);
        assertThat(header.sideInfoSize()).isEqualTo(32);
    }

    @Test
    void readsPaddedMpeg2MonoFrameHeader() throws IOException, InvalidFormatException {
        // MPEG-2, 56 kbit/s, 22.05 kHz, padded, mono.
        final var header = readHeader("FFF372C0");

        assertThat(header).isEqualTo(new FrameHeader(MpegVersion.MPEG_2, 56_000, 22050, 1, ChannelMode.MONO));
        assertThat(header.frameLength()).isEqualTo(72 * 56_000 / 22050 + 1);
        assertThat(header.sideInfoSize()).isEqualTo(9);
    }

    @Test
    void readsMpeg25FrameHeader() throws IOException, InvalidFormatException {
        final var header = readHeader("FFE38800");

        assertThat(header.version()).isEqualTo(MpegVersion.MPEG_2_5);
        assertThat(header.sampleRate()).isEqualTo(8000);
        assertThat(header.bitrate()).isEqualTo(64_000);
    }

    @Test
    void readsWholeHeader() throws IOException, InvalidFormatException {
        final var bits = bits("FFFB906441");
        new FrameHeader(bits);

        assertThat(bits.readBits(8)).isEqualTo(0x41);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                // No frame sync.
                "49443304",
                // Layer II.
                "FFFD9064",
                // Free format.
                "FFFB0064",
                // Bitrate index 15.
                "FFFBF064",
                // Reserved sample rate.
                "FFFB9C64",
                // Reserved MPEG version.
                "FFEB9064"
            })
    void rejectsWhatIsNotALayerIIIHeader(final String hex) {
        assertThatThrownBy(() -> readHeader(hex)).isInstanceOf(InvalidFormatException.class);
    }

    private static FrameHeader readHeader(final String hex) throws IOException, InvalidFormatException {
        return new FrameHeader(bits(hex));
    }

    private static BitInputStream bits(final String hex) {
        return BitInputStream.wrap(new ByteArrayInputStream(HexFormat.of().parseHex(hex)), ByteOrder.BIG_ENDIAN);
    }
}
