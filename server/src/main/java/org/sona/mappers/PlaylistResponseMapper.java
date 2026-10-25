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

    @Mapping(target = ".", source = "playlist")
    PlaylistSummaryResponse toResponse(PlaylistSummary summary);

    @Mapping(target = ".", source = "playlist")
    @Mapping(target = "tracks", source = "entries")
    PlaylistResponse toResponse(PlaylistDetails details);

    @Mapping(target = ".", source = "track")
    PlaylistTrackResponse toResponse(PlaylistEntry entry);
}
