package org.sona.format;

/**
 * Floating-point audio that adds up how long its frames play.
 */
public final class MeasuredFloatAudio extends MeasuredAudio<FloatPcmFrame> implements FloatPcmAudio {

    private final FloatPcmAudio audio;

    public MeasuredFloatAudio(final FloatPcmAudio audio) {
        super(audio.iterator());
        this.audio = audio;
    }

    @Override
    public int sampleRate() {
        return audio.sampleRate();
    }

    @Override
    public int bitsPerSample() {
        return audio.bitsPerSample();
    }
}
