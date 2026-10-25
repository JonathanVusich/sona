package org.sona.format.mp3;

import java.time.Duration;
import org.sona.format.AudioUtils;

/**
 * What an MP3's frames hold, as its first frame and VBR header give it.
 *
 * @param audioStart the offset of the first frame of audio, after any ID3v2 tag and VBR header frame
 * @param channelCount the first frame's channels, which a later frame may change
 * @param frameCount how many frames of audio the stream has
 * @param lameTag whether the stream has a LAME tag, which gives the encoder's delay and padding
 * @param encoderDelay samples of silence the encoder put before the audio, which a LAME tag gives, otherwise 0
 * @param encoderPadding samples of silence the encoder put after the audio to fill the last frame, likewise
 * @param freeFormatLength how many bytes each frame of a free-format stream takes but for its padding, which their
 *     headers don't give, otherwise 0
 */
public record Mp3StreamInfo(
        long audioStart,
        int sampleRate,
        int channelCount,
        int samplesPerFrame,
        long frameCount,
        boolean lameTag,
        int encoderDelay,
        int encoderPadding,
        int freeFormatLength) {

    // The delay of the synthesis filterbank and the IMDCT's overlap, in samples.
    private static final int DECODER_DELAY = 529;

    /**
     * @return samples per channel of the audio that was encoded, without the encoder's delay and padding. The padding
     *     a LAME tag gives counts the decoder's delay, so the audio ends that much before the padding starts, but never
     *     past the last frame, as FFmpeg plays it.
     */
    public long sampleCount() {
        final var decoded = frameCount * samplesPerFrame;
        if (!lameTag) {
            return decoded;
        }
        final var end = Math.min(decoded - encoderPadding + DECODER_DELAY, decoded);
        return Math.max(end - leadingSamples(), 0);
    }

    /**
     * @return how many decoded samples per channel come before the first one that was encoded: the encoder's delay,
     *     and the 529 samples of the decoder's own, which a LAME tag's delay leaves out, even when it's 0. A stream
     *     without one is played from its first sample, as FFmpeg does.
     */
    public long leadingSamples() {
        if (!lameTag) {
            return 0;
        }
        return encoderDelay + DECODER_DELAY;
    }

    /**
     * @return how long the audio plays
     */
    public Duration duration() {
        return AudioUtils.duration(sampleCount(), sampleRate);
    }
}
