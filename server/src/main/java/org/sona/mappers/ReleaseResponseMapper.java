package org.sona.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.sona.controller.response.MediumResponse;
import org.sona.controller.response.ReleaseResponse;
import org.sona.controller.response.TrackResponse;
import org.sona.db.MediumWithTracks;
import org.sona.db.ReleaseDetails;
import org.sona.db.TrackWithArtist;
import org.sona.model.tables.pojos.Medium;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ReleaseResponseMapper {

    @Mapping(target = ".", source = "release")
    @Mapping(target = "formats", source = "media")
    @Mapping(target = "media", source = "media", qualifiedByName = "mediaInLibrary")
    ReleaseResponse toResponse(ReleaseDetails details);

    @Mapping(target = ".", source = "medium")
    MediumResponse toResponse(MediumWithTracks medium);

    @Mapping(target = ".", source = "track")
    TrackResponse toResponse(TrackWithArtist track);

    /**
     * @return the media with a track in the library; a disc with none, as when only one disc of a release was
     * uploaded, still counts among the release's formats, but has nothing to list
     */
    @Named("mediaInLibrary")
    default List<MediumResponse> mediaInLibrary(final List<MediumWithTracks> media) {
        return media.stream()
                .filter(medium -> !medium.tracks().isEmpty())
                .map(this::toResponse)
                .toList();
    }

    /**
     * @return what a release's formats list for the medium
     */
    default String mediumFormat(final MediumWithTracks medium) {
        return mediumFormat(medium.medium());
    }

    /**
     * @return what a release's formats list for the medium
     */
    default String mediumFormat(final Medium medium) {
        return medium.format();
    }
}
