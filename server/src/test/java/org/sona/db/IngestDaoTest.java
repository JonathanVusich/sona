package org.sona.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.samples.IngestUploadUtils.loadGroup;
import static org.sona.samples.IngestUploadUtils.loadIngest;
import static org.sona.utils.IDGenerator.uuidv7;

import java.util.List;
import org.jooq.DSLContext;
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

    @Autowired
    DSLContext dsl;

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

        assertThat(loadIngest(dsl, trackToIngest.trackIngestId())).isEqualTo(trackToIngest);
    }

    @Test
    void setStateSetsTheState() {
        final var uploader =
                new Users(uuidv7(), UserType.LOCAL, "uploader-" + uuidv7(), UserRole.ADMIN, AccountState.ACTIVE);
        userDao.insert(uploader);
        final var group = new IngestGroup(
                uuidv7(), IngestGroupKind.ALBUM, "Uploads", uploader.userId(), IngestGroupState.MATCHED);
        dao.insertGroup(group);
        final var trackToIngest = new TrackToIngest(uuidv7(), group.ingestGroupId(), "filename", IngestState.UPLOADED);
        dao.insertTrackIngest(trackToIngest);

        dao.setState(trackToIngest.trackIngestId(), IngestState.COMPLETED);

        assertThat(loadIngest(dsl, trackToIngest.trackIngestId()).state()).isEqualTo(IngestState.COMPLETED);
    }

    @Test
    void insertGroupStoresItsFilesWithIt() {
        final var group = newGroup(IngestGroupState.CREATED);
        final var first = new TrackToIngest(uuidv7(), group.ingestGroupId(), "01.flac", IngestState.CREATED);
        final var second = new TrackToIngest(uuidv7(), group.ingestGroupId(), "02.flac", IngestState.CREATED);

        dao.insertGroup(group, List.of(first, second));

        assertThat(loadGroup(dsl, group.ingestGroupId())).isEqualTo(group);
        assertThat(loadIngest(dsl, first.trackIngestId())).isEqualTo(first);
        assertThat(loadIngest(dsl, second.trackIngestId())).isEqualTo(second);
    }

    @Test
    void aGroupIsUploadedOnceItsLastFileIs() {
        final var group = storeGroup(IngestGroupState.CREATED);
        final var first = storeTrack(group, IngestState.CREATED);
        final var second = storeTrack(group, IngestState.UPLOADING);

        dao.setUploaded(first.trackIngestId());

        assertThat(loadIngest(dsl, first.trackIngestId()).state()).isEqualTo(IngestState.UPLOADED);
        assertThat(loadGroup(dsl, group.ingestGroupId()).state()).isEqualTo(IngestGroupState.CREATED);

        dao.setUploaded(second.trackIngestId());

        assertThat(loadGroup(dsl, group.ingestGroupId()).state()).isEqualTo(IngestGroupState.UPLOADED);
    }

    @Test
    void uploadedGroupsAreLoadedToBeMatchedOldestFirst() {
        storeGroup(IngestGroupState.CREATED);
        final var matched = storeGroup(IngestGroupState.MATCHED);
        final var oldest = storeGroup(IngestGroupState.UPLOADED);
        final var newest = storeGroup(IngestGroupState.UPLOADED);

        assertThat(dao.loadUploadedGroups()).extracting(UploadedGroup::group).containsExactly(oldest, newest);

        dao.setGroupState(oldest.ingestGroupId(), IngestGroupState.MATCHED);

        assertThat(dao.loadUploadedGroups()).extracting(UploadedGroup::group).containsExactly(newest);
        assertThat(loadGroup(dsl, matched.ingestGroupId())).isEqualTo(matched);
    }

    @Test
    void anUploadedGroupComesWithOnlyItsFilesThatAreUploaded() {
        final var group = storeGroup(IngestGroupState.UPLOADED);
        final var uploaded = storeTrack(group, IngestState.UPLOADED);
        storeTrack(group, IngestState.FAILED);
        final var other = storeGroup(IngestGroupState.UPLOADED);
        final var otherUploaded = storeTrack(other, IngestState.UPLOADED);

        assertThat(dao.loadUploadedGroups())
                .containsExactly(
                        new UploadedGroup(group, List.of(uploaded)), new UploadedGroup(other, List.of(otherUploaded)));
    }

    @Test
    void aFileIsSetUploadingUntilItIsUploaded() {
        final var group = storeGroup(IngestGroupState.CREATED);
        final var created = storeTrack(group, IngestState.CREATED);
        final var uploaded = storeTrack(group, IngestState.UPLOADED);
        final var uploading =
                new TrackToIngest(created.trackIngestId(), group.ingestGroupId(), "filename", IngestState.UPLOADING);

        assertThat(dao.setUploading(created.trackIngestId())).contains(uploading);
        assertThat(dao.setUploading(created.trackIngestId())).contains(uploading);
        assertThat(dao.setUploading(uploaded.trackIngestId())).contains(uploaded);
        assertThat(dao.setUploading(uuidv7())).isEmpty();
    }

    @Test
    void anUploadedFileIsReturnedUploaded() {
        final var group = storeGroup(IngestGroupState.CREATED);
        final var track = storeTrack(group, IngestState.UPLOADING);

        assertThat(dao.setUploaded(track.trackIngestId()).state()).isEqualTo(IngestState.UPLOADED);
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
