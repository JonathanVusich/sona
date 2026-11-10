package org.sona.mappers;

import org.mapstruct.Mapper;
import org.sona.controller.response.QueueEntryResponse;
import org.sona.controller.response.QueueResponse;
import org.sona.db.QueueDetails;
import org.sona.db.QueueEntry;

@Mapper(componentModel = "spring")
public interface QueueResponseMapper {

    QueueResponse toResponse(QueueDetails details);

    QueueEntryResponse toResponse(QueueEntry entry);
}
