package org.sona.mappers;

import org.mapstruct.Mapper;
import org.sona.model.tables.pojos.QueueTrack;
import org.sona.model.tables.records.QueueTrackRecord;

@Mapper(componentModel = "spring")
public interface QueueTrackMapper {

    QueueTrackRecord toRecord(QueueTrack queueTrack);
}
