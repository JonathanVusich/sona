package org.sona.db;

import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.tables.pojos.Label;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

@IntegrationTest
class LabelDaoTest {

    @Autowired
    LabelDao dao;

    @Test
    void upsertAllInsertsNewLabels() {
        final var labels = List.of(new Label(uuidv7(), "Parlophone", uuidv7()), new Label(uuidv7(), "Capitol", uuidv7()));

        assertThat(dao.upsertAll(labels)).containsExactlyInAnyOrderElementsOf(labels);
    }

    @Test
    void upsertAllOverwritesLabelsWithSameMusicBrainzIdAndInsertsTheRest() {
        final var musicbrainzId = uuidv7();
        final var stored = dao.upsertAll(List.of(new Label(uuidv7(), "Old Name", musicbrainzId))).getFirst();
        final var capitol = new Label(uuidv7(), "Capitol", uuidv7());

        final var refreshed = dao.upsertAll(List.of(new Label(uuidv7(), "New Name", musicbrainzId), capitol));

        assertThat(refreshed).containsExactlyInAnyOrder(new Label(stored.labelId(), "New Name", musicbrainzId), capitol);
    }

    @Test
    void upsertAllOfNoLabelsStoresNothing() {
        assertThat(dao.upsertAll(List.of())).isEmpty();
    }
}
