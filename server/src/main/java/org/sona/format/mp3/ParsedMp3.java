package org.sona.format.mp3;

import org.sona.format.ParsedAudio;
import org.sona.metadata.RawMetadata;

/**
 * An MP3 whose tags and stream information have been read, left at its first frame of audio.
 */
public record ParsedMp3(RawMetadata metadata, Mp3Decoder audio) implements ParsedAudio {}
