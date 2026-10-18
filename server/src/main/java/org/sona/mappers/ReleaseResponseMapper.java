package org.sona.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.controller.response.MediumResponse;
import org.sona.controller.response.ReleaseResponse;
import org.sona.controller.response.TrackResponse;
import org.sona.db.MediumWithTracks;
import org.sona.db.ReleaseDetails;
import org.sona.db.TrackWithArtist;
import org.sona.model.tables.pojos.Medium;

@Mapper(componentModel = "spring")
public interface ReleaseResponseMapper {

    @Mapping(target = ".", source = "release")
    @Mapping(target = "formats", source = "allMedia")
    ReleaseResponse toResponse(ReleaseDetails details);

    @Mapping(target = ".", source = "medium")
    MediumResponse toResponse(MediumWithTracks medium);

    @Mapping(target = ".", source = "track")
    TrackResponse toResponse(TrackWithArtist track);

    /**
     * @return what a release's formats list for the medium
     */
    default String mediumFormat(final Medium medium) {
        return medium.format();
    }
}
