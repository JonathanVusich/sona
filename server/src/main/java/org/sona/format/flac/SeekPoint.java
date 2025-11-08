package org.sona.format.flac;

public record SeekPoint(long sampleNumber, long frameOffset, short samplesInFrame) {
}
