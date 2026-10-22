package org.sona.db;

import static org.sona.model.Tables.ARTIST;
import static org.sona.model.Tables.RELEASE;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.ArtistMapper;
import org.sona.mappers.ReleaseDetailsMapper;
import org.sona.mappers.ReleaseMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every table the query reads is converted to its pojo by its table's mapper as it's read, so the composite mappers
 * build their results from pojos only.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class ReleaseDetailsDao {

    private final DSLContext dsl;
    private final ReleaseMapper releaseMapper;
    private final ArtistMapper artistMapper;
    private final ReleaseDetailsMapper detailsMapper;
    private final ReleaseFields releaseFields;

    /**
     * @return the release, if it's in the library: a release is only once one of its tracks is
     */
    public Optional<ReleaseDetails> loadById(final UUID releaseId) {
        return dsl.select(
                        RELEASE.convertFrom(releaseMapper::fromRecord),
                        ARTIST.convertFrom(artistMapper::fromRecord),
                        releaseFields.releaseLabels(RELEASE.RELEASE_ID),
                        releaseFields.releaseMediaWithTracks(RELEASE.RELEASE_ID))
                .from(RELEASE)
                .join(ARTIST)
                .on(ARTIST.ARTIST_ID.eq(RELEASE.ARTIST_ID))
                .where(RELEASE.RELEASE_ID.eq(releaseId))
                .and(RELEASE.TRACK_COUNT.gt(0))
                .fetchOptional(release -> detailsMapper.fromPojos(
                        release.value1(), release.value2(), release.value3(), release.value4()));
    }
}
