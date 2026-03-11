package org.sona.model;

import org.sona.model.tables.interfaces.ITrack;

import java.util.UUID;

public record Track(
    UUID trackId,
    String name,
    Integer duration,
    String format,
    String path,
    UUID artistId,
    String artistName,
    UUID releaseId,
    String releaseName,
    UUID musicbrainzArtistId,
    String musicbrainzArtistName,
    UUID musicbrainzTrackId,
    String musicbrainzTrackName,
    UUID musicbrainzReleaseId,
    String musicbrainzReleaseName
) {}
