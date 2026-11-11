package org.sona.db;

import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.selectFrom;
import static org.jooq.impl.DSL.when;
import static org.sona.model.Tables.INGEST_CANDIDATE;
import static org.sona.model.Tables.INGEST_GROUP;
import static org.sona.model.Tables.TRACK_TO_INGEST;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Records;
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

    /**
     * @return the groups whose files are all uploaded and that haven't been matched yet, oldest first
     */
    public List<UploadedGroup> loadUploadedGroups() {
        final var trackIngests = multiset(selectFrom(TRACK_TO_INGEST)
                        .where(TRACK_TO_INGEST.GROUP_INGEST_ID.eq(INGEST_GROUP.INGEST_GROUP_ID))
                        .and(TRACK_TO_INGEST.STATE.eq(IngestState.UPLOADED))
                        .orderBy(TRACK_TO_INGEST.TRACK_INGEST_ID))
                .convertFrom(rows -> rows.map(trackToIngestMapper::fromRecord));
        return dsl.select(INGEST_GROUP.convertFrom(ingestGroupMapper::fromRecord), trackIngests)
                .from(INGEST_GROUP)
                .where(INGEST_GROUP.STATE.eq(IngestGroupState.UPLOADED))
                .orderBy(INGEST_GROUP.INGEST_GROUP_ID)
                .fetch(Records.mapping(UploadedGroup::new));
    }

    public void setGroupState(final UUID ingestGroupId, final IngestGroupState state) {
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

    /**
     * Sets the file as receiving its bytes, if it hasn't been uploaded yet; one that is uploading may be sent again,
     * and a second request for it may be doing so meanwhile, hence the state in the update. A file already uploaded
     * keeps its state, which the row returned shows, so one statement tells the caller which it was.
     *
     * @return the file's ingest, {@code UPLOADING} unless it had been uploaded, or empty if there is no such file
     */
    public Optional<TrackToIngest> setUploading(final UUID trackToIngestId) {
        return dsl.update(TRACK_TO_INGEST)
                .set(
                        TRACK_TO_INGEST.STATE,
                        when(
                                        TRACK_TO_INGEST.STATE.in(IngestState.CREATED, IngestState.UPLOADING),
                                        inline(IngestState.UPLOADING))
                                .otherwise(TRACK_TO_INGEST.STATE))
                .where(TRACK_TO_INGEST.TRACK_INGEST_ID.eq(trackToIngestId))
                .returning()
                .fetchOptional(trackToIngestMapper::fromRecord);
    }

    /**
     * Sets the file uploaded, and its group too if that was the last of its files to arrive. The group is locked
     * before its files are checked, so of two files finishing at once, the second sees the first's commit.
     *
     * @return the file's ingest, as uploaded
     */
    public TrackToIngest setUploaded(final UUID trackToIngestId) {
        final var uploaded = dsl.update(TRACK_TO_INGEST)
                .set(TRACK_TO_INGEST.STATE, IngestState.UPLOADED)
                .where(TRACK_TO_INGEST.TRACK_INGEST_ID.eq(trackToIngestId))
                .returning()
                .fetchSingle(trackToIngestMapper::fromRecord);
        final var ingestGroupId = uploaded.groupIngestId();
        dsl.select(INGEST_GROUP.INGEST_GROUP_ID)
                .from(INGEST_GROUP)
                .where(INGEST_GROUP.INGEST_GROUP_ID.eq(ingestGroupId))
                .forUpdate()
                .fetch();
        final var stillUploading = dsl.fetchExists(dsl.selectFrom(TRACK_TO_INGEST)
                .where(TRACK_TO_INGEST.GROUP_INGEST_ID.eq(ingestGroupId))
                .and(TRACK_TO_INGEST.STATE.in(IngestState.CREATED, IngestState.UPLOADING)));
        if (!stillUploading) {
            setGroupState(ingestGroupId, IngestGroupState.UPLOADED);
        }
        return uploaded;
    }

    /**
     * @return the ID of the user who uploaded the file
     */
    public UUID setState(final UUID trackToIngestId, final IngestState state) {
        return dsl.update(TRACK_TO_INGEST)
                .set(TRACK_TO_INGEST.STATE, state)
                .from(INGEST_GROUP)
                .where(TRACK_TO_INGEST.TRACK_INGEST_ID.eq(trackToIngestId))
                .and(INGEST_GROUP.INGEST_GROUP_ID.eq(TRACK_TO_INGEST.GROUP_INGEST_ID))
                .returningResult(INGEST_GROUP.USER_ID)
                .fetchSingle(INGEST_GROUP.USER_ID);
    }

    /**
     * Replaces the candidates stored for the ingest, as processing it again finds them anew.
     */
    public void replaceCandidates(final UUID trackToIngestId, final List<IngestCandidate> candidates) {
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
