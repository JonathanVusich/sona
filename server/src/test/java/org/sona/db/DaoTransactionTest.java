package org.sona.db;

import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.model.tables.pojos.Artist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sona.utils.IDGenerator.uuidv7;

@IntegrationTest
class DaoTransactionTest {

    @Autowired
    ArtistDao dao;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aDaoCallOutsideATransactionFails() {
        final var artist = new Artist(uuidv7(), "Coldplay", null, uuidv7());

        assertThatThrownBy(() -> dao.upsert(artist)).isInstanceOf(IllegalTransactionStateException.class);
    }
}
