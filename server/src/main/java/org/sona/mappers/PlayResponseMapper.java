package org.sona.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.controller.response.PlayResponse;
import org.sona.db.PlayedTrack;

@Mapper(componentModel = "spring", uses = PlaylistResponseMapper.class)
public interface PlayResponseMapper {

    @Mapping(target = "playId", source = "play.playId")
    @Mapping(target = "track", source = "entry")
    PlayResponse toResponse(PlayedTrack played);
}
