package org.sona.config;

import org.jooq.ExecuteListenerProvider;
import org.jooq.impl.DefaultExecuteListenerProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Checks that every query the tests run is served by an index.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestQueryPlanConfig {

    @Bean
    ExecuteListenerProvider indexedQueryListener() {
        return new DefaultExecuteListenerProvider(new IndexedQueryListener());
    }
}
