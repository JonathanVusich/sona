package org.sona.mappers;

import org.mapstruct.Mapper;
import org.sona.model.tables.pojos.Playlist;
import org.sona.model.tables.records.PlaylistRecord;

@Mapper(componentModel = "spring")
public interface PlaylistMapper {

    PlaylistRecord toRecord(Playlist playlist);

    Playlist fromRecord(PlaylistRecord playlist);
}
