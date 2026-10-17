package org.sona.controller;

import lombok.RequiredArgsConstructor;
import org.sona.controller.response.ArtistDetailsResponse;
import org.sona.controller.response.ArtistPageResponse;
import org.sona.controller.response.ErrorCode;
import org.sona.db.ArtistDao;
import org.sona.db.ArtistDetailsDao;
import org.sona.exception.ClientException;
import org.sona.mappers.ArtistResponseMapper;
import org.sona.model.tables.pojos.Artist;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('library:read')")
public class ArtistController {

    private static final int MAX_LIMIT = 100;

    private final ArtistDao artistDao;
    private final ArtistDetailsDao detailsDao;
    private final ArtistResponseMapper mapper;

    /**
     * @param after the {@code next} of the page before, or nothing for the first page
     * @param limit how many artists a page holds, up to {@value MAX_LIMIT}
     */
    @GetMapping(ApiRoutes.ARTISTS)
    public ArtistPageResponse artists(@RequestParam(required = false) final UUID after,
                                      @RequestParam(defaultValue = "50") final int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ClientException(ErrorCode.INVALID_PARAMETER);
        }
        // One more than the page holds tells whether there's a page after it.
        if (after == null) {
            return pageResponse(artistDao.loadFirstPage(limit + 1), limit);
        }
        return pageResponse(artistDao.loadNextPage(after, limit + 1), limit);
    }

    @GetMapping(ApiRoutes.ARTIST)
    public ArtistDetailsResponse artist(@PathVariable final UUID artistId) {
        return detailsDao.loadById(artistId)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ClientException(ErrorCode.ARTIST_NOT_FOUND));
    }

    /**
     * @param loaded up to one more artist than the page holds
     */
    private ArtistPageResponse pageResponse(final List<Artist> loaded, final int limit) {
        final var page = loaded.stream().limit(limit).toList();
        final var artists = page.stream().map(mapper::toSummaryResponse).toList();
        if (loaded.size() == page.size()) {
            // The response marks the last page with a null next.
            return new ArtistPageResponse(artists, null);
        }
        return new ArtistPageResponse(artists, page.getLast().artistId());
    }
}
