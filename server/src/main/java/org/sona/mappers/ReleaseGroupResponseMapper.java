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
import org.sona.model.tables.pojos.Medium;

@Mapper(componentModel = "spring", uses = ReleaseResponseMapper.class)
public interface ReleaseGroupResponseMapper {

    @Mapping(target = ".", source = "releaseGroup")
    ReleaseGroupResponse toResponse(ReleaseGroupDetails details);

    @Mapping(target = ".", source = "release")
    @Mapping(target = "formats", source = "media")
    EditionResponse toResponse(Edition edition);

    @Mapping(target = "releaseId", source = "release.releaseId")
    DefaultReleaseResponse toResponse(DefaultRelease defaultRelease);

    @Mapping(target = ".", source = "releaseGroup")
    ReleaseGroupSummaryResponse toResponse(ReleaseGroupSummary summary);

    /**
     * @return what an edition lists for the medium
     */
    default String mediumFormat(final Medium medium) {
        return medium.format();
    }
}
