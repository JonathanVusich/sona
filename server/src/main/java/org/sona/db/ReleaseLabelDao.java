package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.ReleaseLabelMapper;
import org.sona.model.tables.pojos.ReleaseLabel;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static org.sona.model.Tables.RELEASE_LABEL;

@Component
@RequiredArgsConstructor
@Transactional
public class ReleaseLabelDao {

    private final DSLContext dsl;
    private final ReleaseLabelMapper mapper;

    /**
     * Records that the release came out on the label, unless that's already stored.
     */
    public void upsert(final ReleaseLabel releaseLabel) {
        dsl.insertInto(RELEASE_LABEL)
                .set(mapper.toRecord(releaseLabel))
                .onConflictDoNothing()
                .execute();
    }
}
