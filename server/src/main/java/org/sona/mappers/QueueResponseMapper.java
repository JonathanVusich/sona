package org.sona.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.controller.response.QueueEntryResponse;
import org.sona.controller.response.QueueResponse;
import org.sona.db.QueueDetails;
import org.sona.db.QueueEntry;

@Mapper(componentModel = "spring")
public interface QueueResponseMapper {

    QueueResponse toResponse(QueueDetails details);

    @Mapping(target = "track.trackId", source = "trackId")
    @Mapping(target = "track.name", source = "trackName")
    @Mapping(target = "track.duration", source = "duration")
    @Mapping(target = "track.position", source = "position")
    @Mapping(target = "track.artist.artistId", source = "artistId")
    @Mapping(target = "track.artist.name", source = "artistName")
    @Mapping(target = "track.release.releaseId", source = "releaseId")
    @Mapping(target = "track.release.name", source = "releaseName")
    QueueEntryResponse toResponse(QueueEntry entry);
}
