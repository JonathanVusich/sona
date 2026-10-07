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
    void storeAndLoad() {
        final var trackToIngest = new TrackToIngest(uuidv7(), uuidv7(), "filename", IngestState.PENDING);

        dao.store(List.of(trackToIngest));

        assertThat(dao.load(trackToIngest.trackIngestId())).isEqualTo(trackToIngest);
    }

    @Test
    void loadByGroup() {
        final var group = uuidv7();
        final var first = new TrackToIngest(uuidv7(), group, "first.flac", IngestState.PENDING);
        final var second = new TrackToIngest(uuidv7(), group, "second.flac", IngestState.PENDING);
        final var otherGroup = new TrackToIngest(uuidv7(), uuidv7(), "other.flac", IngestState.PENDING);

        dao.store(List.of(first, second, otherGroup));

        assertThat(dao.loadByGroup(group)).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void updateStateRemovesFromPending() {
        final var trackToIngest = new TrackToIngest(uuidv7(), uuidv7(), "filename", IngestState.PENDING);
        dao.store(List.of(trackToIngest));
        assertThat(dao.pending()).contains(trackToIngest);

        dao.updateState(trackToIngest.trackIngestId(), IngestState.COMPLETED);

        assertThat(dao.load(trackToIngest.trackIngestId()).state()).isEqualTo(IngestState.COMPLETED);
        assertThat(dao.pending()).doesNotContain(trackToIngest);
    }
}
