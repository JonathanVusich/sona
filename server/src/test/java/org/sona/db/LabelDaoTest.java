package org.sona.db;

import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.tables.pojos.Label;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

@IntegrationTest
class LabelDaoTest {

    @Autowired
    LabelDao dao;

    @Test
    void upsertInsertsNewLabel() {
        final var label = new Label(uuidv7(), "Parlophone", uuidv7());

        assertThat(dao.upsert(label)).isEqualTo(label);
    }

    @Test
    void upsertOverwritesLabelWithSameMusicBrainzId() {
        final var musicbrainzId = uuidv7();
        final var stored = dao.upsert(new Label(uuidv7(), "Old Name", musicbrainzId));

        final var refreshed = dao.upsert(new Label(uuidv7(), "New Name", musicbrainzId));

        assertThat(refreshed).isEqualTo(new Label(stored.labelId(), "New Name", musicbrainzId));
    }
}
