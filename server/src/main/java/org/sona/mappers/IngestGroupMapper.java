package org.sona.mappers;

import org.mapstruct.Mapper;
import org.sona.model.tables.pojos.IngestGroup;
import org.sona.model.tables.records.IngestGroupRecord;

@Mapper(componentModel = "spring")
public interface IngestGroupMapper {

    IngestGroupRecord toRecord(IngestGroup ingestGroup);

    IngestGroup fromRecord(IngestGroupRecord ingestGroup);
}
