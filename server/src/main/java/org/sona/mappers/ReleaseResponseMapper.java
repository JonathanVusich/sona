package org.sona.mappers;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.sona.controller.response.MediumResponse;
import org.sona.controller.response.ReleaseResponse;
import org.sona.controller.response.TrackResponse;
import org.sona.db.MediumWithTracks;
import org.sona.db.ReleaseDetails;
import org.sona.db.TrackWithArtist;

@Mapper(componentModel = "spring")
public interface ReleaseResponseMapper {

    @Mapping(target = "artist.artistId", source = "artistId")
    @Mapping(target = "artist.name", source = "artistName")
    @Mapping(target = "formats", source = "media")
    @Mapping(target = "media", source = "media", qualifiedByName = "mediaInLibrary")
    ReleaseResponse toResponse(ReleaseDetails details);

    MediumResponse toResponse(MediumWithTracks medium);

    @Mapping(target = "artist.artistId", source = "artistId")
    @Mapping(target = "artist.name", source = "artistName")
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
        return medium.format();
    }
}
