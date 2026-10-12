package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.MediumMapper;
import org.sona.model.tables.pojos.Medium;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static org.jooq.impl.DSL.excluded;
import static org.sona.model.Tables.MEDIUM;

@Component
@RequiredArgsConstructor
@Transactional
public class MediumDao {

    private final DSLContext dsl;
    private final MediumMapper mapper;

    /**
     * Inserts the medium, or overwrites the stored one at the same position of the release with its newer data.
     *
     * @return the stored medium, which keeps its original ID if it already existed
     */
    public Medium upsert(final Medium medium) {
        final var record = dsl.insertInto(MEDIUM)
                .set(mapper.toRecord(medium))
                .onConflict(MEDIUM.RELEASE_ID, MEDIUM.POSITION)
                .doUpdate()
                .set(MEDIUM.FORMAT, excluded(MEDIUM.FORMAT))
                .returning()
                .fetchSingle();
        return mapper.fromRecord(record);
    }
}
