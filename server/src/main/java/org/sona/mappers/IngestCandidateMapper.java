package org.sona.mappers;

import org.mapstruct.Mapper;
import org.sona.model.tables.pojos.IngestCandidate;
import org.sona.model.tables.records.IngestCandidateRecord;

@Mapper(componentModel = "spring")
public interface IngestCandidateMapper {

    IngestCandidateRecord toRecord(IngestCandidate ingestCandidate);
}
