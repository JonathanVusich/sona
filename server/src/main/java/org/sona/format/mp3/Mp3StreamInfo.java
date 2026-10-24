package org.sona.format.mp3;

import java.time.Duration;
import org.sona.format.AudioUtils;

/**
 * What an MP3's frames hold, as its first frame and VBR header give it.
 *
 * @param audioStart the offset of the first frame of audio, after any ID3v2 tag and VBR header frame
 * @param frameCount how many frames of audio the stream has
 * @param encoderDelay samples of silence the encoder put before the audio, which a LAME tag gives, otherwise 0
 * @param encoderPadding samples of silence the encoder put after the audio to fill the last frame, likewise
 */
public record Mp3StreamInfo(
        long audioStart,
        int sampleRate,
        int channelCount,
        int samplesPerFrame,
        long frameCount,
        int encoderDelay,
        int encoderPadding) {

    /**
     * @return samples per channel of the audio that was encoded, without the encoder's delay and padding
     */
    public long sampleCount() {
        return Math.max(frameCount * samplesPerFrame - encoderDelay - encoderPadding, 0);
    }

    /**
     * @return how long the audio plays
     */
    public Duration duration() {
        return AudioUtils.duration(sampleCount(), sampleRate);
    }
}
