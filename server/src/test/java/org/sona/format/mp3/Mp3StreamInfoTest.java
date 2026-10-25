package org.sona.format.mp3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.sona.exception.InvalidFormatException;
import org.sona.samples.Mp3Sample;

class Mp3StreamInfoTest {

    // The ID3v2 tag before the first frame of each sample, and the ID3v1 tag after the last.
    private static final int STEREO_CBR_TAG_SIZE = 291;
    private static final int MONO_VBR_TAG_SIZE = 133;
    private static final int ID3V1_SIZE = 128;

    // The LAME tag gives the encoder's delay and padding, so the duration is exactly the one second encoded. The Info
    // header's frame is silence, not audio, so the audio starts after it.
    @Test
    void takesLengthFromInfoHeaderAndLameTag() throws IOException, InvalidFormatException {
        final var bytes = sampleBytes(Mp3Sample.STEREO_CBR_ID3V24);
        final var streamInfo = readStreamInfo(bytes, STEREO_CBR_TAG_SIZE, bytes.length);

        assertThat(streamInfo.audioStart()).isEqualTo(STEREO_CBR_TAG_SIZE + 417);
        assertThat(streamInfo.duration()).isEqualTo(Duration.ofSeconds(1));
        assertThat(streamInfo.sampleCount()).isEqualTo(44100);
        assertThat(streamInfo.channelCount()).isEqualTo(2);
        assertThat(streamInfo.sampleRate()).isEqualTo(44100);
    }

    @Test
    void readsMpeg2VbrStream() throws IOException, InvalidFormatException {
        final var bytes = sampleBytes(Mp3Sample.MONO_VBR_ID3V23);
        final var streamInfo = readStreamInfo(bytes, MONO_VBR_TAG_SIZE, bytes.length - ID3V1_SIZE);

        // The Xing header's frame takes 182 bytes.
        assertThat(streamInfo).isEqualTo(new Mp3StreamInfo(MONO_VBR_TAG_SIZE + 182, 22050, 1, 576, 41, 576, 990));
        assertThat(streamInfo.duration()).isEqualTo(Duration.ofSeconds(1));
    }

    // Without a VBR header the frames are counted, and the encoder's delay and padding are unknown, so they're counted
    // as audio.
    @Test
    void countsFramesWithoutVbrHeader() throws IOException, InvalidFormatException {
        final var bytes = sampleBytes(Mp3Sample.NO_XING_ID3V1);
        final var streamInfo = readStreamInfo(bytes, 0, bytes.length - ID3V1_SIZE);

        assertThat(streamInfo.audioStart()).isZero();
        assertThat(streamInfo.frameCount()).isEqualTo(40);
        assertThat(streamInfo.duration()).isEqualTo(Duration.ofNanos(40 * 1152 * 1_000_000_000L / 44100));
    }

    // Each free-format frame runs to the next one's sync: 417 bytes, and 418 when padded, as at 128 kbit/s.
    @Test
    void countsFreeFormatFrames() throws IOException, InvalidFormatException {
        final var bytes = sampleBytes(Mp3Sample.FREE_FORMAT);
        final var streamInfo = readStreamInfo(bytes, 0, bytes.length - ID3V1_SIZE);

        assertThat(streamInfo.frameCount()).isEqualTo(40);
        assertThat(streamInfo.duration()).isEqualTo(Duration.ofNanos(40 * 1152 * 1_000_000_000L / 44100));
    }

    // The first frame takes 417 bytes, so the second starts there.
    @Test
    void rejectsDataBetweenFrames() throws IOException {
        final var bytes = sampleBytes(Mp3Sample.NO_XING_ID3V1);
        Arrays.fill(bytes, 417, 417 + FrameHeader.SIZE, (byte) 0);

        assertThatThrownBy(() -> readStreamInfo(bytes, 0, bytes.length - ID3V1_SIZE))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessage("MP3 frame doesn't start with a frame sync!");
    }

    @Test
    void rejectsFrameCutShort() throws IOException {
        final var bytes = sampleBytes(Mp3Sample.NO_XING_ID3V1);

        assertThatThrownBy(() -> readStreamInfo(bytes, 0, bytes.length - ID3V1_SIZE - 200))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessageEndingWith("is cut short!");
    }

    @Test
    void rejectsStreamWithoutFrames() {
        final var bytes = new byte[64];

        assertThatThrownBy(() -> readStreamInfo(bytes, 0, bytes.length))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessage("MP3 frame doesn't start with a frame sync!");
    }

    private static Mp3StreamInfo readStreamInfo(final byte[] bytes, final int audioStart, final int audioEnd)
            throws IOException, InvalidFormatException {
        final var input = new ByteArrayInputStream(bytes, audioStart, bytes.length - audioStart);
        return new Mp3Parser().readStreamInfo(input, audioStart, audioEnd);
    }

    private static byte[] sampleBytes(final Mp3Sample sample) throws IOException {
        try (final var inputStream = sample.inputStream()) {
            return inputStream.readAllBytes();
        }
    }
}
