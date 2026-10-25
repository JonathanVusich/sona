package org.sona.mappers;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.db.PlaylistDetails;
import org.sona.db.PlaylistEntry;
import org.sona.model.tables.pojos.Playlist;

@Mapper(componentModel = "spring")
public interface PlaylistDetailsMapper {

    @Mapping(target = "playlist", source = "playlist")
    @Mapping(target = "entries", source = "entries")
    PlaylistDetails fromPojos(Playlist playlist, List<PlaylistEntry> entries);
}
