package org.sona.mappers;

import org.mapstruct.Mapper;
import org.sona.db.PlayedTrack;
import org.sona.db.PlaylistEntry;
import org.sona.model.tables.pojos.Play;

@Mapper(componentModel = "spring")
public interface PlayedTrackMapper {

    PlayedTrack fromPojos(Play play, PlaylistEntry entry);
}
