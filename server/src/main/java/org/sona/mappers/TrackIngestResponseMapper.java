package org.sona.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.controller.response.TrackIngestResponse;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.TrackToIngest;

@Mapper(componentModel = "spring")
public interface TrackIngestResponseMapper {

    TrackIngestResponse toResponse(TrackToIngest trackToIngest);

    /**
     * @param state the ingest's state since it was loaded
     */
    @Mapping(target = "state", source = "state")
    TrackIngestResponse toResponse(TrackToIngest trackToIngest, IngestState state);
}
