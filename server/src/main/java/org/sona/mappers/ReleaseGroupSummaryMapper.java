package org.sona.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.db.ReleaseGroupSummary;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.ReleaseGroup;

@Mapper(componentModel = "spring")
public interface ReleaseGroupSummaryMapper {

    @Mapping(target = "releaseGroup", source = "releaseGroup")
    @Mapping(target = "artist", source = "artist")
    ReleaseGroupSummary fromPojos(ReleaseGroup releaseGroup, Artist artist);
}
