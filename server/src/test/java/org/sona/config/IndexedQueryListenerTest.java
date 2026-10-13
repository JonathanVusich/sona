package org.sona.config;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sona.model.Tables.ARTIST;
import static org.sona.utils.IDGenerator.uuidv7;

@IntegrationTest
class IndexedQueryListenerTest {

    @Autowired
    DSLContext dsl;

    @Test
    void aQueryAnIndexServesRuns() {
        assertThatCode(() -> dsl.selectFrom(ARTIST).where(ARTIST.ARTIST_ID.eq(uuidv7())).fetch())
                .doesNotThrowAnyException();
    }

    @Test
    void aQueryThatScansAWholeTableFails() {
        assertThatThrownBy(() -> dsl.selectFrom(ARTIST).where(ARTIST.THUMBNAIL_PATH.eq("cover.jpg")).fetch())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Query scans [artist] without an index");
    }
}
