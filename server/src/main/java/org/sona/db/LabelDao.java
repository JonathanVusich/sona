package org.sona.db;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.LabelMapper;
import org.sona.model.tables.pojos.Label;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static org.jooq.impl.DSL.excluded;
import static org.sona.model.Tables.LABEL;

@Component
@RequiredArgsConstructor
@Transactional
public class LabelDao {

    private final DSLContext dsl;
    private final LabelMapper mapper;

    /**
     * Inserts the label, or overwrites the stored one with the same MusicBrainz ID with its newer data.
     *
     * @return the stored label, which keeps its original ID if it already existed
     */
    public Label upsert(final Label label) {
        final var record = dsl.insertInto(LABEL)
                .set(mapper.toRecord(label))
                .onConflict(LABEL.MUSICBRAINZ_LABEL_ID)
                .doUpdate()
                .set(LABEL.NAME, excluded(LABEL.NAME))
                .returning()
                .fetchSingle();
        return mapper.fromRecord(record);
    }
}
