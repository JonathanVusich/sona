package org.sona.mappers;

import org.mapstruct.Mapper;
import org.sona.controller.response.PlayResponse;
import org.sona.db.PlayedTrack;

@Mapper(componentModel = "spring")
public interface PlayResponseMapper {

    PlayResponse toResponse(PlayedTrack played);
}
