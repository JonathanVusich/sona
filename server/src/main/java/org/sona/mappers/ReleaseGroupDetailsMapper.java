package org.sona.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.db.Edition;
import org.sona.db.ReleaseGroupDetails;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.ReleaseGroup;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ReleaseGroupDetailsMapper {

    @Mapping(target = "releaseGroup", source = "releaseGroup")
    @Mapping(target = "artist", source = "artist")
    @Mapping(target = "editions", source = "editions")
    ReleaseGroupDetails fromPojos(ReleaseGroup releaseGroup, Artist artist, List<Edition> editions);
}
