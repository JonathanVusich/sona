package org.sona.config;

import org.jooq.ExecuteListenerProvider;
import org.jooq.impl.DefaultExecuteListenerProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Fails any read of a page that takes more than one query ({@link SingleQueryListener}).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestSingleQueryConfig {

    @Bean
    ExecuteListenerProvider singleQueryListener() {
        return new DefaultExecuteListenerProvider(new SingleQueryListener());
    }

    @Bean
    SingleQueryFilter singleQueryFilter() {
        return new SingleQueryFilter();
    }
}
