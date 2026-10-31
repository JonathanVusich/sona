package org.sona.db;

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
import org.sona.model.enums.IngestGroupState;
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

    /**
     * Stores a group together with the ingest of each of its files, so the group never exists without them.
     */
    public void insertGroup(final IngestGroup ingestGroup, final List<TrackToIngest> trackIngests) {
        insertGroup(ingestGroup);
        final var records =
                trackIngests.stream().map(trackToIngestMapper::toRecord).toList();
        dsl.insertInto(TRACK_TO_INGEST)
                .columns(TRACK_TO_INGEST.fields())
                .valuesOfRecords(records)
                .execute();
    }

    public Optional<IngestGroup> loadGroupById(final UUID ingestGroupId) {
        return dsl.selectFrom(INGEST_GROUP)
                .where(INGEST_GROUP.INGEST_GROUP_ID.eq(ingestGroupId))
                .fetchOptional(ingestGroupMapper::fromRecord);
    }

    /**
     * @return the oldest group whose files are all uploaded and that hasn't been matched yet
     */
    public Optional<IngestGroup> loadOldestUploadedGroup() {
        return dsl.selectFrom(INGEST_GROUP)
                .where(INGEST_GROUP.STATE.eq(IngestGroupState.UPLOADED))
                .orderBy(INGEST_GROUP.INGEST_GROUP_ID)
                .limit(1)
                .fetchOptional(ingestGroupMapper::fromRecord);
    }

    public void updateGroupState(final UUID ingestGroupId, final IngestGroupState state) {
        dsl.update(INGEST_GROUP)
                .set(INGEST_GROUP.STATE, state)
                .where(INGEST_GROUP.INGEST_GROUP_ID.eq(ingestGroupId))
                .execute();
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
     * @return the file's ingest, if the group is the one it was created in
     */
    public Optional<TrackToIngest> loadGroupTrackIngest(final UUID ingestGroupId, final UUID trackToIngestId) {
        return dsl.selectFrom(TRACK_TO_INGEST)
                .where(TRACK_TO_INGEST.GROUP_INGEST_ID.eq(ingestGroupId))
                .and(TRACK_TO_INGEST.TRACK_INGEST_ID.eq(trackToIngestId))
                .fetchOptional(trackToIngestMapper::fromRecord);
    }

    /**
     * @return the group's ingests that are uploaded and waiting to be processed, oldest first
     */
    public List<TrackToIngest> loadUploadedByGroup(final UUID ingestGroupId) {
        return dsl.selectFrom(TRACK_TO_INGEST)
                .where(TRACK_TO_INGEST.GROUP_INGEST_ID.eq(ingestGroupId))
                .and(TRACK_TO_INGEST.STATE.eq(IngestState.UPLOADED))
                .orderBy(TRACK_TO_INGEST.TRACK_INGEST_ID)
                .fetch(trackToIngestMapper::fromRecord);
    }

    /**
     * Marks the file uploaded, and its group too if that was the last of its files to arrive. The group is locked
     * first, so two files finishing at once can't each see the other as still uploading.
     */
    public void updateUploaded(final UUID trackToIngestId) {
        final var ingestGroupId = loadTrackIngestById(trackToIngestId).groupIngestId();
        dsl.select(INGEST_GROUP.INGEST_GROUP_ID)
                .from(INGEST_GROUP)
                .where(INGEST_GROUP.INGEST_GROUP_ID.eq(ingestGroupId))
                .forUpdate()
                .fetch();
        updateState(trackToIngestId, IngestState.UPLOADED);
        final var stillUploading = dsl.fetchExists(dsl.selectFrom(TRACK_TO_INGEST)
                .where(TRACK_TO_INGEST.GROUP_INGEST_ID.eq(ingestGroupId))
                .and(TRACK_TO_INGEST.STATE.in(IngestState.CREATED, IngestState.UPLOADING)));
        if (!stillUploading) {
            updateGroupState(ingestGroupId, IngestGroupState.UPLOADED);
        }
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
