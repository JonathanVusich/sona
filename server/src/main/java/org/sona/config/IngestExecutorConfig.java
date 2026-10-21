package org.sona.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class IngestExecutorConfig {

    /**
     * The name {@code @Async} methods that process ingests give.
     */
    public static final String INGEST_EXECUTOR = "ingestExecutor";

    /**
     * One thread, so no two passes over the pending ingests take the same one; MusicBrainz takes about one request a
     * second, so a second thread wouldn't go faster. While a pass runs, one more may wait: it takes every ingest that
     * arrives in the meantime, so any further pass asked for then is dropped rather than refused.
     */
    @Bean(INGEST_EXECUTOR)
    public ThreadPoolTaskExecutor ingestExecutor() {
        final var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.setThreadNamePrefix("ingest-");
        return executor;
    }
}
