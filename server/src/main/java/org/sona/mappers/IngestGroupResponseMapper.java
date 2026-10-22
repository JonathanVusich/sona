package org.sona.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.controller.response.IngestGroupResponse;
import org.sona.model.tables.pojos.IngestGroup;

@Mapper(componentModel = "spring")
public interface IngestGroupResponseMapper {

    @Mapping(target = "groupIngestId", source = "ingestGroupId")
    IngestGroupResponse toResponse(IngestGroup ingestGroup);
}
