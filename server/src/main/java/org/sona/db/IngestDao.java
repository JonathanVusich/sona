package org.sona.db;

import static org.jooq.impl.DSL.field;
import static org.sona.model.Tables.INGEST_CANDIDATE;
import static org.sona.model.Tables.INGEST_GROUP;
import static org.sona.model.Tables.TRACK_TO_INGEST;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.mappers.IngestCandidateMapper;
import org.sona.mappers.IngestGroupMapper;
import org.sona.mappers.TrackToIngestMapper;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.IngestCandidate;
import org.sona.model.tables.pojos.IngestGroup;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Uploads: the ingest groups uploaders create, and the ingest of each file uploaded into one.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class IngestDao {

    private final DSLContext dsl;
    private final IngestGroupMapper ingestGroupMapper;
    private final TrackToIngestMapper trackToIngestMapper;
    private final IngestCandidateMapper ingestCandidateMapper;

    public void insertGroup(final IngestGroup ingestGroup) {
        dsl.insertInto(INGEST_GROUP)
                .set(ingestGroupMapper.toRecord(ingestGroup))
                .execute();
    }

    public Optional<IngestGroup> loadGroupById(final UUID ingestGroupId) {
        return dsl.selectFrom(INGEST_GROUP)
                .where(INGEST_GROUP.INGEST_GROUP_ID.eq(ingestGroupId))
                .fetchOptional(ingestGroupMapper::fromRecord);
    }

    public void insertTrackIngest(final TrackToIngest trackToIngest) {
        dsl.insertInto(TRACK_TO_INGEST)
                .set(trackToIngestMapper.toRecord(trackToIngest))
                .execute();
    }

    public TrackToIngest loadTrackIngestById(final UUID trackToIngestId) {
        return dsl.selectFrom(TRACK_TO_INGEST)
                .where(TRACK_TO_INGEST.TRACK_INGEST_ID.eq(trackToIngestId))
                .fetchSingle(trackToIngestMapper::fromRecord);
    }

    /**
     * @return the oldest pending ingest whose group has had all its files uploaded; one of a group still uploading
     * waits, as matching chooses one release for the whole group
     */
    public Optional<TrackToIngest> loadOldestReadyPending() {
        final var uploaded = TRACK_TO_INGEST.as("uploaded");
        final var uploadedCount =
                dsl.selectCount().from(uploaded).where(uploaded.GROUP_INGEST_ID.eq(TRACK_TO_INGEST.GROUP_INGEST_ID));
        return dsl.select(TRACK_TO_INGEST.fields())
                .from(TRACK_TO_INGEST)
                .join(INGEST_GROUP)
                .on(INGEST_GROUP.INGEST_GROUP_ID.eq(TRACK_TO_INGEST.GROUP_INGEST_ID))
                .where(TRACK_TO_INGEST.STATE.eq(IngestState.PENDING))
                .and(field(uploadedCount).ge(INGEST_GROUP.TRACK_COUNT))
                .orderBy(TRACK_TO_INGEST.TRACK_INGEST_ID)
                .limit(1)
                .fetchOptional()
                .map(record -> record.into(TRACK_TO_INGEST))
                .map(trackToIngestMapper::fromRecord);
    }

    /**
     * @return the group's ingests still waiting to be processed, oldest first
     */
    public List<TrackToIngest> loadPendingByGroup(final UUID ingestGroupId) {
        return dsl.selectFrom(TRACK_TO_INGEST)
                .where(TRACK_TO_INGEST.GROUP_INGEST_ID.eq(ingestGroupId))
                .and(TRACK_TO_INGEST.STATE.eq(IngestState.PENDING))
                .orderBy(TRACK_TO_INGEST.TRACK_INGEST_ID)
                .fetch(trackToIngestMapper::fromRecord);
    }

    public void updateState(final UUID trackToIngestId, final IngestState state) {
        dsl.update(TRACK_TO_INGEST)
                .set(TRACK_TO_INGEST.STATE, state)
                .where(TRACK_TO_INGEST.TRACK_INGEST_ID.eq(trackToIngestId))
                .execute();
    }

    /**
     * Replaces the candidates stored for the ingest, as processing it again finds them anew.
     */
    public void updateCandidates(final UUID trackToIngestId, final List<IngestCandidate> candidates) {
        deleteCandidates(trackToIngestId);
        if (candidates.isEmpty()) {
            return;
        }
        final var records =
                candidates.stream().map(ingestCandidateMapper::toRecord).toList();
        dsl.insertInto(INGEST_CANDIDATE)
                .columns(INGEST_CANDIDATE.fields())
                .valuesOfRecords(records)
                .execute();
    }

    /**
     * Deletes the candidates stored for the ingest, once it no longer needs a choice between them.
     */
    public void deleteCandidates(final UUID trackToIngestId) {
        dsl.deleteFrom(INGEST_CANDIDATE)
                .where(INGEST_CANDIDATE.TRACK_INGEST_ID.eq(trackToIngestId))
                .execute();
    }
}
