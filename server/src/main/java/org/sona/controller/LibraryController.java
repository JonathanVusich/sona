package org.sona.controller;

import lombok.RequiredArgsConstructor;
import org.sona.controller.request.ReleaseGroupOrder;
import org.sona.controller.response.ArtistDetailResponse;
import org.sona.controller.response.ArtistResponse;
import org.sona.controller.response.ErrorCode;
import org.sona.controller.response.ReleaseDetailResponse;
import org.sona.controller.response.ReleaseGroupDetailResponse;
import org.sona.controller.response.ReleaseGroupResponse;
import org.sona.controller.response.ReleaseResponse;
import org.sona.controller.response.TrackResponse;
import org.sona.db.ArtistDao;
import org.sona.db.ReleaseDao;
import org.sona.db.ReleaseDetails;
import org.sona.db.ReleaseGroupDao;
import org.sona.db.TrackDao;
import org.sona.exception.ClientException;
import org.sona.mappers.LibraryResponseMapper;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.sona.model.tables.pojos.Track;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

/**
 * Browsing the library. Lists come a page at a time: a client asks for the next page with the ID of the last item
 * it has, until a page comes back empty. A release group's earliest release is its main release, whose tracks stand
 * for the group's.
 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('library:read')")
public class LibraryController {

    private static final int PAGE_SIZE = 100;

    private final ArtistDao artistDao;
    private final ReleaseGroupDao releaseGroupDao;
    private final ReleaseDao releaseDao;
    private final TrackDao trackDao;
    private final LibraryResponseMapper mapper;

    @GetMapping(ApiRoutes.ARTISTS)
    public List<ArtistResponse> artists(@RequestParam(required = false) final UUID after) {
        final var artists = artistDao.loadReleaseArtists(after, PAGE_SIZE);
        return artists.stream().map(mapper::toResponse).toList();
    }

    @GetMapping(ApiRoutes.ARTIST)
    public ArtistDetailResponse artist(@PathVariable final UUID artistId) {
        final var artist = artistDao.loadById(artistId)
                .orElseThrow(() -> new ClientException(ErrorCode.ARTIST_NOT_FOUND));
        final var releaseGroups = releaseGroupDao.loadByArtist(artistId);
        final var releaseGroupIds = releaseGroups.stream().map(ReleaseGroup::releaseGroupId).toList();
        final var mainReleaseIds = releaseDao.loadMainReleaseIds(releaseGroupIds);
        final var tracks = trackResponses(trackDao.loadByReleases(mainReleaseIds));
        final var releaseGroupResponses = releaseGroups.stream()
                .map(releaseGroup -> mapper.toResponse(releaseGroup, artist))
                .toList();
        return mapper.toResponse(artist, releaseGroupResponses, tracks);
    }

    @GetMapping(ApiRoutes.RELEASE_GROUPS)
    public List<ReleaseGroupResponse> releaseGroups(
            @RequestParam(defaultValue = "NAME") final ReleaseGroupOrder order,
            @RequestParam(required = false) final UUID after) {
        final var releaseGroups = switch (order) {
            case NAME -> releaseGroupDao.loadByName(after, PAGE_SIZE);
            case NEWEST -> releaseGroupDao.loadNewest(after, PAGE_SIZE);
        };
        return releaseGroups.stream()
                .map(releaseGroup -> mapper.toResponse(releaseGroup.releaseGroup(), releaseGroup.artist()))
                .toList();
    }

    @GetMapping(ApiRoutes.RELEASE_GROUP)
    public ReleaseGroupDetailResponse releaseGroup(@PathVariable final UUID releaseGroupId) {
        final var releaseGroup = releaseGroupDao.loadWithArtist(releaseGroupId)
                .orElseThrow(() -> new ClientException(ErrorCode.RELEASE_GROUP_NOT_FOUND));
        final var releases = releaseDao.loadDetailsByReleaseGroup(releaseGroupId);
        final var mainReleaseIds = releases.stream().limit(1).map(details -> details.release().releaseId()).toList();
        final var tracks = trackResponses(trackDao.loadByReleases(mainReleaseIds));
        return mapper.toResponse(releaseGroup.releaseGroup(), releaseGroup.artist(), releaseResponses(releases), tracks);
    }

    @GetMapping(ApiRoutes.RELEASE)
    public ReleaseDetailResponse release(@PathVariable final UUID releaseId) {
        final var release = releaseDao.loadDetails(releaseId)
                .orElseThrow(() -> new ClientException(ErrorCode.RELEASE_NOT_FOUND));
        final var tracks = trackResponses(trackDao.loadByReleases(List.of(releaseId)));
        return mapper.toResponse(release, tracks);
    }

    private List<ReleaseResponse> releaseResponses(final List<ReleaseDetails> releases) {
        final var releaseIds = releases.stream().map(details -> details.release().releaseId()).toList();
        final var trackCounts = trackDao.countByReleases(releaseIds);
        return releases.stream()
                .map(details -> mapper.toResponse(details, trackCounts.getOrDefault(details.release().releaseId(), 0)))
                .toList();
    }

    /**
     * @return the tracks, each with its release's name and its artist
     */
    private List<TrackResponse> trackResponses(final List<Track> tracks) {
        final var releaseIds = tracks.stream().map(Track::releaseId).collect(toSet());
        final var releases = releaseDao.loadByIds(releaseIds).stream()
                .collect(toMap(Release::releaseId, identity()));
        final var artists = artistsById(tracks.stream().map(Track::artistId).collect(toSet()));
        return tracks.stream()
                .map(track -> mapper.toResponse(track, releases.get(track.releaseId()), artists.get(track.artistId())))
                .toList();
    }

    private Map<UUID, Artist> artistsById(final Collection<UUID> artistIds) {
        return artistDao.loadByIds(artistIds).stream().collect(toMap(Artist::artistId, identity()));
    }
}
