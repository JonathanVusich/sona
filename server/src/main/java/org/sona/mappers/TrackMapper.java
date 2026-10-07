package org.sona.mappers;

import org.mapstruct.Mapper;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.records.TrackRecord;

@Mapper(componentModel = "spring")
public interface TrackMapper {

    TrackRecord toRecord(Track track);
    Track fromRecord(TrackRecord track);
}
