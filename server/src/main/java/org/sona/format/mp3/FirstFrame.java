package org.sona.format.mp3;

/**
 * What an MP3's first frame holds: a VBR header, which encoders put in a frame of silence before the audio so players
 * know its length without reading every frame, or audio.
 */
sealed interface FirstFrame permits FirstFrame.VbrHeader, FirstFrame.AudioFrame {

    /**
     * A Xing (or Info) or VBRI header with the stream's frame count.
     *
     * @param frameCount frames of audio, not counting the one holding this header
     * @param lameTag whether a LAME tag (or FFmpeg's, which it writes the same) follows a Xing header
     * @param encoderDelay from the LAME tag, otherwise 0
     * @param encoderPadding likewise
     */
    record VbrHeader(long frameCount, boolean lameTag, int encoderDelay, int encoderPadding) implements FirstFrame {}

    /**
     * A frame of audio, or a VBR header without a frame count, so the stream's frames are counted.
     */
    record AudioFrame() implements FirstFrame {}
}
