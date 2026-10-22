package org.sona.mappers;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.db.MediumWithTracks;
import org.sona.db.TrackWithArtist;
import org.sona.model.tables.pojos.Medium;

@Mapper(componentModel = "spring")
public interface MediumWithTracksMapper {

    @Mapping(target = "medium", source = "medium")
    @Mapping(target = "tracks", source = "tracks")
    MediumWithTracks fromPojos(Medium medium, List<TrackWithArtist> tracks);
}
