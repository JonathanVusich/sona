package org.sona.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.db.ArtistDetails;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.ReleaseGroup;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ArtistDetailsMapper {

    @Mapping(target = "artist", source = "artist")
    @Mapping(target = "releaseGroups", source = "releaseGroups")
    ArtistDetails fromPojos(Artist artist, List<ReleaseGroup> releaseGroups);
}
