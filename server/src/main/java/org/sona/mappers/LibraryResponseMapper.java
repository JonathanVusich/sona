package org.sona.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sona.controller.response.ArtistDetailResponse;
import org.sona.controller.response.ArtistResponse;
import org.sona.controller.response.ReleaseDetailResponse;
import org.sona.controller.response.ReleaseGroupDetailResponse;
import org.sona.controller.response.ReleaseGroupResponse;
import org.sona.controller.response.ReleaseResponse;
import org.sona.controller.response.TrackResponse;
import org.sona.db.ReleaseDetails;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.Label;
import org.sona.model.tables.pojos.Medium;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.sona.model.tables.pojos.Track;

import java.util.List;
import java.util.Objects;

/**
 * Builds the library's API responses from its rows.
 */
@Mapper(componentModel = "spring")
public interface LibraryResponseMapper {

    ArtistResponse toResponse(Artist artist);

    @Mapping(target = "artistId", source = "artist.artistId")
    @Mapping(target = "name", source = "artist.name")
    @Mapping(target = "country", source = "artist.country")
    ArtistDetailResponse toResponse(Artist artist, List<ReleaseGroupResponse> releaseGroups,
                                    List<TrackResponse> tracks);

    @Mapping(target = "name", source = "releaseGroup.name")
    @Mapping(target = "artist", source = "artist")
    ReleaseGroupResponse toResponse(ReleaseGroup releaseGroup, Artist artist);

    @Mapping(target = "releaseGroupId", source = "releaseGroup.releaseGroupId")
    @Mapping(target = "name", source = "releaseGroup.name")
    @Mapping(target = "type", source = "releaseGroup.type")
    @Mapping(target = "firstReleaseDate", source = "releaseGroup.firstReleaseDate")
    @Mapping(target = "artist", source = "artist")
    ReleaseGroupDetailResponse toResponse(ReleaseGroup releaseGroup, Artist artist, List<ReleaseResponse> releases,
                                          List<TrackResponse> tracks);

    @Mapping(target = "releaseId", source = "details.release.releaseId")
    @Mapping(target = "name", source = "details.release.name")
    @Mapping(target = "date", source = "details.release.date")
    @Mapping(target = "country", source = "details.release.country")
    @Mapping(target = "labels", source = "details.labels")
    @Mapping(target = "formats", source = "details.media")
    ReleaseResponse toResponse(ReleaseDetails details, int trackCount);

    @Mapping(target = "releaseId", source = "details.release.releaseId")
    @Mapping(target = "name", source = "details.release.name")
    @Mapping(target = "date", source = "details.release.date")
    @Mapping(target = "country", source = "details.release.country")
    @Mapping(target = "labels", source = "details.labels")
    @Mapping(target = "formats", source = "details.media")
    @Mapping(target = "releaseGroupId", source = "details.release.releaseGroupId")
    @Mapping(target = "artist", source = "details.artist")
    ReleaseDetailResponse toResponse(ReleaseDetails details, List<TrackResponse> tracks);

    @Mapping(target = "trackId", source = "track.trackId")
    @Mapping(target = "name", source = "track.name")
    @Mapping(target = "releaseId", source = "track.releaseId")
    @Mapping(target = "releaseName", source = "release.name")
    TrackResponse toResponse(Track track, Release release, Artist artist);

    default List<String> labelNames(final List<Label> labels) {
        return labels.stream().map(Label::name).toList();
    }

    default List<String> formats(final List<Medium> media) {
        return media.stream().map(Medium::format).filter(Objects::nonNull).toList();
    }
}
