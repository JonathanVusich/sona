package org.sona.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.IngestGroupKind;
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
        final var group = new IngestGroup(uuidv7(), IngestGroupKind.ALBUM, "Uploads", uploader.userId());
        dao.insertGroup(group);
        final var trackToIngest = new TrackToIngest(uuidv7(), group.ingestGroupId(), "filename", IngestState.PENDING);

        dao.insertTrackIngest(trackToIngest);

        assertThat(dao.loadTrackIngestById(trackToIngest.trackIngestId())).isEqualTo(trackToIngest);
    }

    @Test
    void updateStateSetsTheState() {
        final var uploader =
                new Users(uuidv7(), UserType.LOCAL, "uploader-" + uuidv7(), UserRole.ADMIN, AccountState.ACTIVE);
        userDao.insert(uploader);
        final var group = new IngestGroup(uuidv7(), IngestGroupKind.ALBUM, "Uploads", uploader.userId());
        dao.insertGroup(group);
        final var trackToIngest = new TrackToIngest(uuidv7(), group.ingestGroupId(), "filename", IngestState.PENDING);
        dao.insertTrackIngest(trackToIngest);

        dao.updateState(trackToIngest.trackIngestId(), IngestState.COMPLETED);

        assertThat(dao.loadTrackIngestById(trackToIngest.trackIngestId()).state())
                .isEqualTo(IngestState.COMPLETED);
    }
}
