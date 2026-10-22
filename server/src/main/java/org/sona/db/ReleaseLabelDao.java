package org.sona.db;

import static org.sona.model.Tables.RELEASE_LABEL;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.ReleaseLabelMapper;
import org.sona.model.tables.pojos.ReleaseLabel;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class ReleaseLabelDao {

    private final DSLContext dsl;
    private final ReleaseLabelMapper mapper;

    /**
     * Records in one statement which labels releases came out on, skipping those already stored.
     */
    public void upsertAll(final List<ReleaseLabel> releaseLabels) {
        if (releaseLabels.isEmpty()) {
            return;
        }
        final var records = releaseLabels.stream().map(mapper::toRecord).toList();
        dsl.insertInto(RELEASE_LABEL)
                .columns(RELEASE_LABEL.fields())
                .valuesOfRecords(records)
                .onConflictDoNothing()
                .execute();
    }
}
