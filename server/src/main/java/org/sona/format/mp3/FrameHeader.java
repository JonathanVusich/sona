package org.sona.format.mp3;

import dev.javax.bitstream.BitInputStream;
import java.io.IOException;
import org.sona.exception.InvalidFormatException;

/**
 * The four-byte header of a Layer III frame (ISO/IEC 11172-3 section 2.4.1.3).
 *
 * @param bitrate in bits per second, or {@link #FREE_FORMAT_BITRATE} for a free-format frame until its stream gives its
 *     length (see {@link #withFreeFormatLength})
 * @param padding 1 when the frame has an extra byte to keep the stream at its bitrate, otherwise 0
 */
public record FrameHeader(MpegVersion version, int bitrate, int sampleRate, int padding, ChannelMode channelMode) {

    public static final int SIZE = 4;
    // A free-format frame's header gives no bitrate: the encoder chose one off the table.
    public static final int FREE_FORMAT_BITRATE = 0;

    private static final int SYNC = 0x7FF;
    private static final int LAYER_III = 0b01;

    /**
     * Reads the header, which must be a Layer III frame's.
     */
    public FrameHeader(final BitInputStream bits) throws IOException, InvalidFormatException {
        if (bits.readBits(11) != SYNC) {
            throw new InvalidFormatException("MP3 frame doesn't start with a frame sync!");
        }
        final var version = MpegVersion.fromBits((int) bits.readBits(2));
        if (bits.readBits(2) != LAYER_III) {
            throw new InvalidFormatException("MP3 frame isn't Layer III!");
        }
        // The protection bit says whether a CRC follows the header.
        bits.readBits(1);
        final var bitrate = version.bitrate((int) bits.readBits(4));
        final var sampleRate = version.sampleRate((int) bits.readBits(2));
        final var padding = (int) bits.readBits(1);
        // The private bit is the encoder's own. The mode extension, copyright, original and emphasis fields follow the
        // channel mode.
        bits.readBits(1);
        final var channelMode = ChannelMode.values()[(int) bits.readBits(2)];
        bits.readBits(6);
        this(version, bitrate, sampleRate, padding, channelMode);
    }

    /**
     * @param unpaddedLength how many bytes the stream's frames take without padding, header included
     * @return this free-format frame's header, with the bitrate that gives that length
     */
    public FrameHeader withFreeFormatLength(final int unpaddedLength) {
        // frameLength is slotFactor * bitrate / sampleRate, rounded down. Rounding this bitrate up gives back exactly
        // the length, as the most it adds is slotFactor / sampleRate, which is less than a byte at every sample rate.
        final var slotFactor = version.getSamplesPerFrame() / 8;
        final var scaledLength = (long) unpaddedLength * sampleRate;
        final var freeFormatBitrate = (int) ((scaledLength + slotFactor - 1) / slotFactor);
        return new FrameHeader(version, freeFormatBitrate, sampleRate, padding, channelMode);
    }

    /**
     * @return how many bytes the frame takes, header included
     * @throws IllegalStateException for a free-format frame that hasn't been given its length
     */
    public int frameLength() {
        if (bitrate == FREE_FORMAT_BITRATE) {
            throw new IllegalStateException("A free-format frame's length comes from its stream");
        }
        return version.getSamplesPerFrame() / 8 * bitrate / sampleRate + padding;
    }

    public int channelCount() {
        return switch (channelMode) {
            case STEREO, JOINT_STEREO, DUAL_CHANNEL -> 2;
            case MONO -> 1;
        };
    }

    /**
     * @return how many bytes of side information follow the header (and its CRC, if it has one)
     */
    public int sideInfoSize() {
        return switch (version) {
            case MPEG_1 -> sideInfoSize(17, 32);
            case MPEG_2, MPEG_2_5 -> sideInfoSize(9, 17);
        };
    }

    /**
     * @return whether the other frame can follow this one in a stream, which keeps its version and sample rate
     */
    public boolean isSameStream(final FrameHeader other) {
        return version == other.version() && sampleRate == other.sampleRate();
    }

    private int sideInfoSize(final int monoSize, final int stereoSize) {
        return switch (channelMode) {
            case STEREO, JOINT_STEREO, DUAL_CHANNEL -> stereoSize;
            case MONO -> monoSize;
        };
    }
}
