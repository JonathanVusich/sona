package org.sona.format.mp3;

/**
 * The four-byte header of a Layer III frame (ISO/IEC 11172-3 section 2.4.1.3), which {@link Mp3Parser} reads.
 *
 * @param protection whether a CRC follows the header
 * @param bitrate in bits per second, or {@link #FREE_FORMAT_BITRATE} for a free-format frame until its stream gives its
 *     length (see {@link #withFreeFormatLength})
 * @param padding 1 when the frame has an extra byte to keep the stream at its bitrate, otherwise 0
 * @param modeExtension in joint stereo, which stereo coding the frame uses: bit 1 for mid/side, bit 0 for intensity
 */
public record FrameHeader(
        MpegVersion version,
        Protection protection,
        int bitrate,
        int sampleRate,
        int padding,
        ChannelMode channelMode,
        int modeExtension) {

    public static final int SIZE = 4;
    // A free-format frame's header gives no bitrate: the encoder chose one off the table.
    public static final int FREE_FORMAT_BITRATE = 0;

    /**
     * How many samples per channel a granule holds.
     */
    public static final int GRANULE_SIZE = 576;

    private static final int INTENSITY_STEREO = 0b01;
    private static final int MID_SIDE_STEREO = 0b10;

    /**
     * @return whether the frame codes its channels with intensity stereo, which joint stereo's mode extension says
     */
    public boolean isIntensityStereo() {
        return channelMode == ChannelMode.JOINT_STEREO && (modeExtension & INTENSITY_STEREO) != 0;
    }

    /**
     * @return whether the frame codes its channels as their sum and difference, which joint stereo's mode extension
     *     says
     */
    public boolean isMidSideStereo() {
        return channelMode == ChannelMode.JOINT_STEREO && (modeExtension & MID_SIDE_STEREO) != 0;
    }

    /**
     * @return how many granules of 576 samples the frame holds: two in MPEG-1, and one in MPEG-2
     */
    public int granuleCount() {
        return version.getSamplesPerFrame() / GRANULE_SIZE;
    }

    /**
     * @return how many bytes of main data the frame holds after its header, CRC and side information
     */
    public int mainDataSize() {
        return frameLength() - SIZE - protection.crcSize() - sideInfoSize();
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
        return new FrameHeader(version, protection, freeFormatBitrate, sampleRate, padding, channelMode, modeExtension);
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

    private int sideInfoSize(final int monoSize, final int stereoSize) {
        return switch (channelMode) {
            case STEREO, JOINT_STEREO, DUAL_CHANNEL -> stereoSize;
            case MONO -> monoSize;
        };
    }
}
