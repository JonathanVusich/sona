package org.sona.format.mp3;

import java.util.Iterator;
import org.sona.format.PcmAudio;
import org.sona.format.PcmFrame;

/**
 * The audio of an MP3, which {@link Mp3Parser#parse} opens in its {@link ParsedMp3}. Its samples will be decoded to
 * 16 bits, as Layer III has no bit depth of its own.
 */
public final class Mp3Decoder implements PcmAudio {

    private static final int BITS_PER_SAMPLE = 16;

    private final Mp3StreamInfo streamInfo;

    Mp3Decoder(final Mp3StreamInfo streamInfo) {
        this.streamInfo = streamInfo;
    }

    public Mp3StreamInfo streamInfo() {
        return streamInfo;
    }

    @Override
    public int sampleRate() {
        return streamInfo.sampleRate();
    }

    @Override
    public int bitsPerSample() {
        return BITS_PER_SAMPLE;
    }

    /**
     * @throws UnsupportedOperationException always, until Sona decodes Layer III
     */
    @Override
    public Iterator<PcmFrame> iterator() {
        // TODO: Decode Layer III frames (ISO/IEC 11172-3 and 13818-3) from the channel the file was parsed from.
        throw new UnsupportedOperationException("MP3 audio can't be decoded yet!");
    }
}
