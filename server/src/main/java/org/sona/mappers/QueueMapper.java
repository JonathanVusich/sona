package org.sona.mappers;

import org.mapstruct.Mapper;
import org.sona.model.tables.pojos.Queue;
import org.sona.model.tables.records.QueueRecord;

@Mapper(componentModel = "spring")
public interface QueueMapper {

    Queue fromRecord(QueueRecord queue);
}
