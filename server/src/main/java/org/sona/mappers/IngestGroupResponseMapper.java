package org.sona.mappers;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.controller.response.IngestGroupResponse;
import org.sona.model.tables.pojos.IngestGroup;
import org.sona.model.tables.pojos.TrackToIngest;

@Mapper(componentModel = "spring", uses = TrackIngestResponseMapper.class)
public interface IngestGroupResponseMapper {

    @Mapping(target = "groupIngestId", source = "ingestGroup.ingestGroupId")
    @Mapping(target = "kind", source = "ingestGroup.kind")
    @Mapping(target = "name", source = "ingestGroup.name")
    @Mapping(target = "tracks", source = "trackIngests")
    IngestGroupResponse toResponse(IngestGroup ingestGroup, List<TrackToIngest> trackIngests);
}
