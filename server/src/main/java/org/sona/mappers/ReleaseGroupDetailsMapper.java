package org.sona.mappers;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.db.DefaultRelease;
import org.sona.db.Edition;
import org.sona.db.ReleaseGroupDetails;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.ReleaseGroup;

@Mapper(componentModel = "spring")
public interface ReleaseGroupDetailsMapper {

    @Mapping(target = "releaseGroup", source = "releaseGroup")
    @Mapping(target = "artist", source = "artist")
    @Mapping(target = "defaultRelease", source = "defaultRelease")
    @Mapping(target = "editions", source = "editions")
    ReleaseGroupDetails fromPojos(
            ReleaseGroup releaseGroup, Artist artist, DefaultRelease defaultRelease, List<Edition> editions);
}
