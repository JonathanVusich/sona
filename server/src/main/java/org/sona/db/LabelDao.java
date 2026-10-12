package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.LabelMapper;
import org.sona.model.tables.pojos.Label;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.jooq.impl.DSL.excluded;
import static org.sona.model.Tables.LABEL;

@Component
@RequiredArgsConstructor
@Transactional
public class LabelDao {

    private final DSLContext dsl;
    private final LabelMapper mapper;

    /**
     * Inserts the labels in one statement, overwriting stored ones with the same MusicBrainz ID with their newer data.
     *
     * @return the stored labels, which keep their original IDs if they already existed, in no particular order
     */
    public List<Label> upsertAll(final List<Label> labels) {
        if (labels.isEmpty()) {
            return List.of();
        }
        final var records = labels.stream().map(mapper::toRecord).toList();
        return dsl.insertInto(LABEL)
                .columns(LABEL.fields())
                .valuesOfRecords(records)
                .onConflict(LABEL.MUSICBRAINZ_LABEL_ID)
                .doUpdate()
                .set(LABEL.NAME, excluded(LABEL.NAME))
                .returning()
                .fetch(mapper::fromRecord);
    }
}
