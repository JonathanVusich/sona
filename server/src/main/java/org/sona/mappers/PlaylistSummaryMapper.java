package org.sona.mappers;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.db.PlaylistSummary;
import org.sona.model.tables.pojos.Playlist;

@Mapper(componentModel = "spring")
public interface PlaylistSummaryMapper {

    @Mapping(target = "playlist", source = "playlist")
    @Mapping(target = "coverNames", source = "coverNames")
    PlaylistSummary fromPojos(Playlist playlist, List<String> coverNames);
}
