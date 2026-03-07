package org.sona.metadata.resolver;

import java.util.Arrays;
import java.util.List;

public enum MusicBrainzTag {
    TRACK_ID("musicbrainz_trackid"),
    RELEASE_TRACK_ID("musicbrainz_releasetrackid"),
    ALBUM_ID("musicbrainz_albumid"),
    ARTIST_ID("musicbrainz_artistid"),
    ALBUM_ARTIST_ID("musicbrainz_albumartistid"),
    RELEASE_GROUP_ID("musicbrainz_releasegroupid");

    private final List<String> matchingTags;

    MusicBrainzTag(String... matchingTags) {
        this.matchingTags = Arrays.asList(matchingTags);
    }

    public List<String> matchingTags() {
        return matchingTags;
    }
}
