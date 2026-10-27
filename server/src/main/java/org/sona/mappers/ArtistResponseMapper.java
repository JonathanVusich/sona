package org.sona.mappers;

import org.mapstruct.Mapper;
import org.sona.controller.response.ArtistDetailsResponse;
import org.sona.controller.response.ArtistReleaseGroupResponse;
import org.sona.controller.response.ArtistSummaryResponse;
import org.sona.db.ArtistDetails;
import org.sona.db.ArtistReleaseGroup;
import org.sona.db.ArtistSummary;

@Mapper(componentModel = "spring")
public interface ArtistResponseMapper {

    ArtistDetailsResponse toResponse(ArtistDetails details);

    ArtistReleaseGroupResponse toResponse(ArtistReleaseGroup releaseGroup);

    ArtistSummaryResponse toSummaryResponse(ArtistSummary artist);
}
