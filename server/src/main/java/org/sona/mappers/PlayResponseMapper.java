package org.sona.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.controller.response.PlayResponse;
import org.sona.db.PlayedTrack;

@Mapper(componentModel = "spring")
public interface PlayResponseMapper {

    @Mapping(target = "track.trackId", source = "trackId")
    @Mapping(target = "track.name", source = "trackName")
    @Mapping(target = "track.duration", source = "duration")
    @Mapping(target = "track.position", source = "position")
    @Mapping(target = "track.artist.artistId", source = "artistId")
    @Mapping(target = "track.artist.name", source = "artistName")
    @Mapping(target = "track.release.releaseId", source = "releaseId")
    @Mapping(target = "track.release.name", source = "releaseName")
    PlayResponse toResponse(PlayedTrack played);
}
