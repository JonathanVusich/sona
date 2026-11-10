package org.sona.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.controller.response.PlaylistResponse;
import org.sona.controller.response.PlaylistSummaryResponse;
import org.sona.controller.response.PlaylistTrackResponse;
import org.sona.db.PlaylistDetails;
import org.sona.db.PlaylistSummary;
import org.sona.db.TrackSummary;

@Mapper(componentModel = "spring")
public interface PlaylistResponseMapper {

    PlaylistSummaryResponse toResponse(PlaylistSummary summary);

    @Mapping(target = "tracks", source = "entries")
    PlaylistResponse toResponse(PlaylistDetails details);

    PlaylistTrackResponse toResponse(TrackSummary track);
}
