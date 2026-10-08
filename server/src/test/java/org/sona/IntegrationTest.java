package org.sona;


import org.junit.jupiter.api.extension.ExtendWith;
import org.sona.config.TestLibraryConfig;
import org.sona.config.TestcontainersConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Full application context against a Testcontainers Postgres (requires Docker), an in-memory library
 * filesystem and stubbed MusicBrainz responses. Writes commit as they would in production, and the tables are
 * truncated after each test.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfig.class, TestLibraryConfig.class})
@ExtendWith(TruncateTablesExtension.class)
public @interface IntegrationTest {
}
