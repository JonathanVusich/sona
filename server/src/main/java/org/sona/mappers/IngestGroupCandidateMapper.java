package org.sona.mappers;

import org.mapstruct.Mapper;
import org.sona.model.tables.pojos.IngestGroupCandidate;
import org.sona.model.tables.records.IngestGroupCandidateRecord;

@Mapper(componentModel = "spring")
public interface IngestGroupCandidateMapper {

    IngestGroupCandidateRecord toRecord(IngestGroupCandidate ingestGroupCandidate);
}
