package org.sona.format.mp3;

import lombok.Getter;
import org.sona.exception.InvalidFormatException;

/**
 * The MPEG audio versions an MP3's frames may be in: MPEG-1 (ISO/IEC 11172-3), MPEG-2's lower sample rates (ISO/IEC
 * 13818-3), and the unofficial MPEG-2.5's lower ones still.
 */
public enum MpegVersion {
    MPEG_1(
            new int[] {44100, 48000, 32000},
            new int[] {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320},
            1152),
    MPEG_2(
            new int[] {22050, 24000, 16000},
            new int[] {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160},
            576),
    MPEG_2_5(
            new int[] {11025, 12000, 8000},
            new int[] {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160},
            576);

    private static final int RESERVED_SAMPLE_RATE = 3;
    private static final int INVALID_BITRATE = 15;

    private final int[] sampleRates;
    // Layer III's bitrates in kbit/s, by the header's bitrate index. Index 0 is free format, and 15 is invalid.
    private final int[] bitrates;
    // Per channel, in each frame.
    @Getter
    private final int samplesPerFrame;

    MpegVersion(final int[] sampleRates, final int[] bitrates, final int samplesPerFrame) {
        this.sampleRates = sampleRates;
        this.bitrates = bitrates;
        this.samplesPerFrame = samplesPerFrame;
    }

    /**
     * @param versionBits a frame header's version field
     */
    public static MpegVersion fromBits(final int versionBits) throws InvalidFormatException {
        return switch (versionBits) {
            case 0b00 -> MPEG_2_5;
            case 0b10 -> MPEG_2;
            case 0b11 -> MPEG_1;
            default -> throw new InvalidFormatException("MP3 frame's MPEG version is reserved!");
        };
    }

    /**
     * @return how many long bands a mixed block has below its short bands: 36 lines, in either version
     */
    int mixedLongBands() {
        return switch (this) {
            case MPEG_1 -> 8;
            case MPEG_2, MPEG_2_5 -> 6;
        };
    }

    /**
     * @param index a frame header's sample rate index, of which 3 is reserved
     */
    public int sampleRate(final int index) throws InvalidFormatException {
        if (index == RESERVED_SAMPLE_RATE) {
            throw new InvalidFormatException("MP3 frame's sample rate is reserved!");
        }
        return sampleRates[index];
    }

    /**
     * @param index a frame header's bitrate index, of which 15 is invalid
     * @return the bitrate in bits per second, or {@link FrameHeader#FREE_FORMAT_BITRATE} for index 0, free format
     */
    public int bitrate(final int index) throws InvalidFormatException {
        if (index == INVALID_BITRATE) {
            throw new InvalidFormatException("MP3 frame's bitrate index is invalid!");
        }
        return bitrates[index] * 1000;
    }
}
