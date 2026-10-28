package org.sona.format;

/**
 * Integer audio that adds up how long its frames play.
 */
public final class MeasuredIntAudio extends MeasuredAudio<IntPcmFrame> implements IntPcmAudio {

    private final IntPcmAudio audio;

    public MeasuredIntAudio(final IntPcmAudio audio) {
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
