package org.sona.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.controller.response.PlaylistResponse;
import org.sona.controller.response.PlaylistSummaryResponse;
import org.sona.controller.response.PlaylistTrackResponse;
import org.sona.db.PlaylistDetails;
import org.sona.db.PlaylistEntry;
import org.sona.db.PlaylistSummary;

@Mapper(componentModel = "spring")
public interface PlaylistResponseMapper {

    PlaylistSummaryResponse toResponse(PlaylistSummary summary);

    @Mapping(target = "tracks", source = "entries")
    PlaylistResponse toResponse(PlaylistDetails details);

    @Mapping(target = "trackId", source = "trackId")
    @Mapping(target = "name", source = "trackName")
    @Mapping(target = "artist.artistId", source = "artistId")
    @Mapping(target = "artist.name", source = "artistName")
    @Mapping(target = "release.releaseId", source = "releaseId")
    @Mapping(target = "release.name", source = "releaseName")
    PlaylistTrackResponse toResponse(PlaylistEntry entry);
}
