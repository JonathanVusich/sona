package org.sona.format.mp3;

import dev.javax.bitstream.BitInputStream;
import java.io.IOException;
import org.sona.exception.InvalidFormatException;

/**
 * The ID3v2 versions Sona reads, which lay out frame headers alike but size and flag them differently.
 */
enum Id3Version {
    V2_3,
    V2_4;

    // The tag header's unsynchronisation flag. In ID3v2.4, every frame carries the flag as well.
    static final int UNSYNCHRONISED_TAG = 0x80;

    // Format flags, the second byte of a frame's flags, in ID3v2.3.0 section 3.3.1.
    private static final int V2_3_COMPRESSED = 0x80;
    private static final int V2_3_ENCRYPTED = 0x40;
    private static final int V2_3_GROUPED = 0x20;

    // Format flags in ID3v2.4.0 structure section 4.1.2.
    private static final int V2_4_GROUPED = 0x40;
    private static final int V2_4_COMPRESSED = 0x08;
    private static final int V2_4_ENCRYPTED = 0x04;
    private static final int V2_4_UNSYNCHRONISED = 0x02;
    private static final int V2_4_DATA_LENGTH = 0x01;

    /**
     * Reads a frame header's size field.
     *
     * @return how many bytes the frame's data takes
     */
    long readFrameSize(final BitInputStream bits) throws IOException, InvalidFormatException {
        return switch (this) {
            case V2_3 -> bits.readBits(32);
            case V2_4 -> Id3Utils.readSynchsafeInt(bits);
        };
    }

    /**
     * Reads the extended header's size field.
     *
     * @return how many bytes of the extended header follow the field
     */
    long readExtendedHeaderSize(final BitInputStream bits) throws IOException, InvalidFormatException {
        return switch (this) {
            // ID3v2.3's size leaves out the four bytes of the size itself, where ID3v2.4's counts them.
            case V2_3 -> bits.readBits(32);
            case V2_4 -> Id3Utils.readSynchsafeInt(bits) - Integer.BYTES;
        };
    }

    /**
     * @return whether the frame is compressed or encrypted, which Sona can't read, so skips
     */
    boolean isEncoded(final int formatFlags) {
        // TODO: Inflate compressed frames.
        final var encodedFlags = switch (this) {
            case V2_3 -> V2_3_COMPRESSED | V2_3_ENCRYPTED;
            case V2_4 -> V2_4_COMPRESSED | V2_4_ENCRYPTED;
        };
        return (formatFlags & encodedFlags) != 0;
    }

    /**
     * @return whether the frame's content must be resynchronised: ID3v2.3 unsynchronises the whole tag rather than
     *     each frame, so the tag reader undoes it there
     */
    boolean isUnsynchronisedFrame(final int tagFlags, final int formatFlags) {
        return switch (this) {
            case V2_3 -> false;
            case V2_4 -> (formatFlags & V2_4_UNSYNCHRONISED) != 0 || (tagFlags & UNSYNCHRONISED_TAG) != 0;
        };
    }

    /**
     * @return how many bytes of fields come before a frame's content when its flags say they're there: the group ID,
     *     and in ID3v2.4 the data length indicator
     */
    int fieldsSize(final int formatFlags) {
        return switch (this) {
            case V2_3 -> v23FieldsSize(formatFlags);
            case V2_4 -> v24FieldsSize(formatFlags);
        };
    }

    private static int v23FieldsSize(final int formatFlags) {
        if ((formatFlags & V2_3_GROUPED) != 0) {
            return 1;
        }
        return 0;
    }

    private static int v24FieldsSize(final int formatFlags) {
        var size = 0;
        if ((formatFlags & V2_4_GROUPED) != 0) {
            size += 1;
        }
        if ((formatFlags & V2_4_DATA_LENGTH) != 0) {
            size += Integer.BYTES;
        }
        return size;
    }
}
