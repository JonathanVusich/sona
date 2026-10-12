package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.MediumMapper;
import org.sona.model.tables.pojos.Medium;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.jooq.impl.DSL.excluded;
import static org.sona.model.Tables.MEDIUM;

@Component
@RequiredArgsConstructor
@Transactional
public class MediumDao {

    private final DSLContext dsl;
    private final MediumMapper mapper;

    /**
     * Inserts the media in one statement, overwriting stored ones at the same position of their release with their
     * newer data.
     *
     * @return the stored media, which keep their original IDs if they already existed, in no particular order
     */
    public List<Medium> upsertAll(final List<Medium> media) {
        if (media.isEmpty()) {
            return List.of();
        }
        final var records = media.stream().map(mapper::toRecord).toList();
        return dsl.insertInto(MEDIUM)
                .columns(MEDIUM.fields())
                .valuesOfRecords(records)
                .onConflict(MEDIUM.RELEASE_ID, MEDIUM.POSITION)
                .doUpdate()
                .set(MEDIUM.FORMAT, excluded(MEDIUM.FORMAT))
                .returning()
                .fetch(mapper::fromRecord);
    }
}
