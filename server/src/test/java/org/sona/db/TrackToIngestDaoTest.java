package org.sona.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class TrackToIngestDaoTest {

    @Autowired
    TrackToIngestDao dao;

    @Test
    void insertAndLoad() {
        final var trackToIngest = new TrackToIngest(uuidv7(), uuidv7(), "filename", IngestState.PENDING);

        dao.insert(trackToIngest);

        assertThat(dao.loadById(trackToIngest.trackIngestId())).isEqualTo(trackToIngest);
    }

    @Test
    void updateStateSetsTheState() {
        final var trackToIngest = new TrackToIngest(uuidv7(), uuidv7(), "filename", IngestState.PENDING);
        dao.insert(trackToIngest);

        dao.updateState(trackToIngest.trackIngestId(), IngestState.COMPLETED);

        assertThat(dao.loadById(trackToIngest.trackIngestId()).state()).isEqualTo(IngestState.COMPLETED);
    }
}
