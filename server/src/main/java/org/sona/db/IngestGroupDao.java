package org.sona.db;

import static org.jooq.impl.DSL.excluded;
import static org.sona.model.Tables.INGEST_GROUP;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.IngestGroupMapper;
import org.sona.model.tables.pojos.IngestGroup;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class IngestGroupDao {

    private final DSLContext dsl;
    private final IngestGroupMapper mapper;

    /**
     * Stores the group, or overwrites its kind and name if it's stored, so the uploader can send it again after a
     * connection breaks.
     */
    public void upsert(final IngestGroup ingestGroup) {
        dsl.insertInto(INGEST_GROUP)
                .set(mapper.toRecord(ingestGroup))
                .onConflict(INGEST_GROUP.INGEST_GROUP_ID)
                .doUpdate()
                .set(INGEST_GROUP.KIND, excluded(INGEST_GROUP.KIND))
                .set(INGEST_GROUP.NAME, excluded(INGEST_GROUP.NAME))
                .execute();
    }

    public Optional<IngestGroup> loadById(final UUID ingestGroupId) {
        return dsl.selectFrom(INGEST_GROUP)
                .where(INGEST_GROUP.INGEST_GROUP_ID.eq(ingestGroupId))
                .fetchOptional(mapper::fromRecord);
    }
}
