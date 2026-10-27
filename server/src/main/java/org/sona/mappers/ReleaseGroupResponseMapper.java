package org.sona.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.controller.response.DefaultReleaseResponse;
import org.sona.controller.response.EditionResponse;
import org.sona.controller.response.ReleaseGroupResponse;
import org.sona.controller.response.ReleaseGroupSummaryResponse;
import org.sona.db.DefaultRelease;
import org.sona.db.Edition;
import org.sona.db.ReleaseGroupDetails;
import org.sona.db.ReleaseGroupSummary;

@Mapper(componentModel = "spring", uses = ReleaseResponseMapper.class)
public interface ReleaseGroupResponseMapper {

    @Mapping(target = "artist.artistId", source = "artistId")
    @Mapping(target = "artist.name", source = "artistName")
    ReleaseGroupResponse toResponse(ReleaseGroupDetails details);

    EditionResponse toResponse(Edition edition);

    @Mapping(target = "media", source = "media", qualifiedByName = "mediaInLibrary")
    DefaultReleaseResponse toResponse(DefaultRelease defaultRelease);

    @Mapping(target = "artist.artistId", source = "artistId")
    @Mapping(target = "artist.name", source = "artistName")
    ReleaseGroupSummaryResponse toResponse(ReleaseGroupSummary summary);
}
