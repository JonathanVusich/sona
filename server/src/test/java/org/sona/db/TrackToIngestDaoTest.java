package org.sona.db;

import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

@IntegrationTest
class TrackToIngestDaoTest {

    @Autowired
    TrackToIngestDao dao;

    @Test
    void fetchAndRetrievePending() {
        final var trackToIngest = new TrackToIngest(uuidv7(), uuidv7(), "filename", IngestState.PENDING);

        dao.store(List.of(trackToIngest));

        final var retrieved = dao.load(trackToIngest.trackIngestId());

        assertThat(trackToIngest).isEqualTo(retrieved);
    }
}