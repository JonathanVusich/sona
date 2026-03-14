package org.sona.config;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DataSourceConnectionProvider;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.DefaultDSLContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

import javax.sql.DataSource;

@Configuration
public class JooqConfig {

    @Bean
    public DSLContext dsl(org.jooq.Configuration configuration) {
        return new DefaultDSLContext(configuration);
    }

    @Bean
    public org.jooq.Configuration config(Settings settings, DataSourceConnectionProvider connectionProvider) {
        final var config = new DefaultConfiguration();
        config.set(connectionProvider);
        config.setSettings(settings);
        config.set(SQLDialect.POSTGRES);
        return config;
    }

    @Bean
    public Settings settings() {
        return new Settings();
    }
}

