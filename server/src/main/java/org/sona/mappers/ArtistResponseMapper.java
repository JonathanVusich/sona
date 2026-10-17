package org.sona.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.controller.response.ArtistDetailsResponse;
import org.sona.controller.response.ArtistReleaseGroupResponse;
import org.sona.controller.response.ArtistSummaryResponse;
import org.sona.db.ArtistDetails;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.ReleaseGroup;

@Mapper(componentModel = "spring")
public interface ArtistResponseMapper {

    @Mapping(target = ".", source = "artist")
    ArtistDetailsResponse toResponse(ArtistDetails details);

    ArtistReleaseGroupResponse toResponse(ReleaseGroup releaseGroup);

    ArtistSummaryResponse toSummaryResponse(Artist artist);
}
