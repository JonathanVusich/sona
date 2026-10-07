package org.sona.format.flac;

import java.util.List;

// https://www.rfc-editor.org/rfc/rfc9639.html#name-cuesheet
public record CueSheet(
        BlockHeader header,
        String mediaCatalogNumber,
        long leadInSamples,
        boolean compactDisc,
        List<CueSheetTrack> tracks
) implements Block {
}
