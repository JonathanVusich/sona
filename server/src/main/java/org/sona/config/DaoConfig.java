package org.sona.config;

import org.jooq.DSLContext;
import org.sona.db.TrackToIngestDao;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DaoConfig {

    @Bean
    public TrackToIngestDao trackToIngestDao(DSLContext context) {
        return new TrackToIngestDao(context);
    }
}
