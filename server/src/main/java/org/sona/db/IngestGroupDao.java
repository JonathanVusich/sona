package org.sona.db;

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

    public void insert(final IngestGroup ingestGroup) {
        dsl.insertInto(INGEST_GROUP).set(mapper.toRecord(ingestGroup)).execute();
    }

    public Optional<IngestGroup> loadById(final UUID ingestGroupId) {
        return dsl.selectFrom(INGEST_GROUP)
                .where(INGEST_GROUP.INGEST_GROUP_ID.eq(ingestGroupId))
                .fetchOptional(mapper::fromRecord);
    }
}
