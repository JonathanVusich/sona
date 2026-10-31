package org.sona.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.IngestGroupKind;
import org.sona.model.enums.IngestGroupState;
import org.sona.model.enums.IngestState;
import org.sona.model.enums.UserRole;
import org.sona.model.enums.UserType;
import org.sona.model.tables.pojos.IngestGroup;
import org.sona.model.tables.pojos.TrackToIngest;
import org.sona.model.tables.pojos.Users;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class IngestDaoTest {

    @Autowired
    UserDao userDao;

    @Autowired
    IngestDao dao;

    @Test
    void insertAndLoad() {
        final var uploader =
                new Users(uuidv7(), UserType.LOCAL, "uploader-" + uuidv7(), UserRole.ADMIN, AccountState.ACTIVE);
        userDao.insert(uploader);
        final var group = new IngestGroup(
                uuidv7(), IngestGroupKind.ALBUM, "Uploads", uploader.userId(), IngestGroupState.MATCHED);
        dao.insertGroup(group);
        final var trackToIngest = new TrackToIngest(uuidv7(), group.ingestGroupId(), "filename", IngestState.UPLOADED);

        dao.insertTrackIngest(trackToIngest);

        assertThat(dao.loadTrackIngestById(trackToIngest.trackIngestId())).isEqualTo(trackToIngest);
    }

    @Test
    void updateStateSetsTheState() {
        final var uploader =
                new Users(uuidv7(), UserType.LOCAL, "uploader-" + uuidv7(), UserRole.ADMIN, AccountState.ACTIVE);
        userDao.insert(uploader);
        final var group = new IngestGroup(
                uuidv7(), IngestGroupKind.ALBUM, "Uploads", uploader.userId(), IngestGroupState.MATCHED);
        dao.insertGroup(group);
        final var trackToIngest = new TrackToIngest(uuidv7(), group.ingestGroupId(), "filename", IngestState.UPLOADED);
        dao.insertTrackIngest(trackToIngest);

        dao.updateState(trackToIngest.trackIngestId(), IngestState.COMPLETED);

        assertThat(dao.loadTrackIngestById(trackToIngest.trackIngestId()).state())
                .isEqualTo(IngestState.COMPLETED);
    }

    @Test
    void insertGroupStoresItsFilesWithIt() {
        final var group = newGroup(IngestGroupState.CREATED);
        final var first = new TrackToIngest(uuidv7(), group.ingestGroupId(), "01.flac", IngestState.CREATED);
        final var second = new TrackToIngest(uuidv7(), group.ingestGroupId(), "02.flac", IngestState.CREATED);

        dao.insertGroup(group, List.of(first, second));

        assertThat(dao.loadGroupById(group.ingestGroupId())).contains(group);
        assertThat(dao.loadTrackIngestById(first.trackIngestId())).isEqualTo(first);
        assertThat(dao.loadTrackIngestById(second.trackIngestId())).isEqualTo(second);
    }

    @Test
    void aGroupIsUploadedOnceItsLastFileIs() {
        final var group = storeGroup(IngestGroupState.CREATED);
        final var first = storeTrack(group, IngestState.CREATED);
        final var second = storeTrack(group, IngestState.UPLOADING);

        dao.updateUploaded(first.trackIngestId());

        assertThat(dao.loadTrackIngestById(first.trackIngestId()).state()).isEqualTo(IngestState.UPLOADED);
        assertThat(dao.loadGroupById(group.ingestGroupId()).orElseThrow().state())
                .isEqualTo(IngestGroupState.CREATED);

        dao.updateUploaded(second.trackIngestId());

        assertThat(dao.loadGroupById(group.ingestGroupId()).orElseThrow().state())
                .isEqualTo(IngestGroupState.UPLOADED);
    }

    @Test
    void uploadedGroupsAreLoadedToBeMatchedOldestFirst() {
        storeGroup(IngestGroupState.CREATED);
        final var matched = storeGroup(IngestGroupState.MATCHED);
        final var oldest = storeGroup(IngestGroupState.UPLOADED);
        final var newest = storeGroup(IngestGroupState.UPLOADED);

        assertThat(dao.loadUploadedGroups()).containsExactly(oldest, newest);

        dao.updateGroupState(oldest.ingestGroupId(), IngestGroupState.MATCHED);

        assertThat(dao.loadUploadedGroups()).containsExactly(newest);
        assertThat(dao.loadGroupById(matched.ingestGroupId())).contains(matched);
    }

    @Test
    void onlyTheUploadedFilesOfAGroupAreLoadedToBeMatched() {
        final var group = storeGroup(IngestGroupState.UPLOADED);
        final var uploaded = storeTrack(group, IngestState.UPLOADED);
        storeTrack(group, IngestState.FAILED);
        storeTrack(storeGroup(IngestGroupState.UPLOADED), IngestState.UPLOADED);

        assertThat(dao.loadUploadedByGroup(group.ingestGroupId())).containsExactly(uploaded);
    }

    @Test
    void anIngestMovesOnOnlyFromTheStateItIsIn() {
        final var group = storeGroup(IngestGroupState.UPLOADED);
        final var track = storeTrack(group, IngestState.UPLOADED);

        assertThat(dao.updateState(track.trackIngestId(), IngestState.INPUT_REQUIRED, IngestState.FAILED))
                .isFalse();
        assertThat(dao.loadTrackIngestById(track.trackIngestId()).state()).isEqualTo(IngestState.UPLOADED);

        assertThat(dao.updateState(track.trackIngestId(), IngestState.UPLOADED, IngestState.FAILED))
                .isTrue();
        assertThat(dao.loadTrackIngestById(track.trackIngestId()).state()).isEqualTo(IngestState.FAILED);
    }

    @Test
    void aFileIsMarkedUploadingUntilItIsUploaded() {
        final var group = storeGroup(IngestGroupState.CREATED);
        final var created = storeTrack(group, IngestState.CREATED);
        final var uploaded = storeTrack(group, IngestState.UPLOADED);

        assertThat(dao.updateUploading(group.ingestGroupId(), created.trackIngestId()))
                .hasValueSatisfying(ingest -> assertThat(ingest.state()).isEqualTo(IngestState.UPLOADING));
        assertThat(dao.updateUploading(group.ingestGroupId(), created.trackIngestId()))
                .isPresent();
        assertThat(dao.updateUploading(group.ingestGroupId(), uploaded.trackIngestId()))
                .isEmpty();
        assertThat(dao.updateUploading(uuidv7(), created.trackIngestId())).isEmpty();
    }

    @Test
    void aFileIsLoadedOnlyThroughTheGroupItWasCreatedIn() {
        final var group = storeGroup(IngestGroupState.CREATED);
        final var track = storeTrack(group, IngestState.CREATED);

        assertThat(dao.loadGroupTrackIngest(group.ingestGroupId(), track.trackIngestId()))
                .contains(track);
        assertThat(dao.loadGroupTrackIngest(uuidv7(), track.trackIngestId())).isEmpty();
    }

    private IngestGroup storeGroup(final IngestGroupState state) {
        final var group = newGroup(state);
        dao.insertGroup(group);
        return group;
    }

    private IngestGroup newGroup(final IngestGroupState state) {
        final var uploader =
                new Users(uuidv7(), UserType.LOCAL, "uploader-" + uuidv7(), UserRole.ADMIN, AccountState.ACTIVE);
        userDao.insert(uploader);
        return new IngestGroup(uuidv7(), IngestGroupKind.ALBUM, "Uploads", uploader.userId(), state);
    }

    private TrackToIngest storeTrack(final IngestGroup group, final IngestState state) {
        final var track = new TrackToIngest(uuidv7(), group.ingestGroupId(), "filename", state);
        dao.insertTrackIngest(track);
        return track;
    }
}
