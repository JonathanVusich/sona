package org.sona.db;

import org.junit.jupiter.api.Test;
import org.sona.config.DaoConfig;
import org.sona.config.DataSourceConfig;
import org.sona.config.JooqConfig;
import org.sona.config.MapperConfig;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

@SpringBootTest(classes = {
        DaoConfig.class,
        DataSourceConfig.class,
        JooqConfig.class,
        MapperConfig.class
}, webEnvironment = SpringBootTest.WebEnvironment.NONE)
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