package org.sona.mappers;

import org.mapstruct.Mapper;
import org.sona.controller.response.TrackIngestResponse;
import org.sona.model.tables.pojos.TrackToIngest;

@Mapper(componentModel = "spring")
public interface TrackIngestResponseMapper {

    TrackIngestResponse toResponse(TrackToIngest trackToIngest);
}
