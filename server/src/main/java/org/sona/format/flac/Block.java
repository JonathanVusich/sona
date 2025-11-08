package org.sona.format.flac;

public sealed interface Block permits Application, CueSheet, Padding, Picture, SeekTable, StreamInfo, VorbisComment {

    BlockHeader header();
}
