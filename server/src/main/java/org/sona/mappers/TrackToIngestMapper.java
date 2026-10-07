package org.sona.mappers;

import org.mapstruct.Mapper;
import org.sona.model.tables.pojos.TrackToIngest;
import org.sona.model.tables.records.TrackToIngestRecord;

@Mapper(componentModel = "spring")
public interface TrackToIngestMapper {

    TrackToIngestRecord toRecord(TrackToIngest trackToIngest);
    TrackToIngest fromRecord(TrackToIngestRecord trackToIngest);
}
