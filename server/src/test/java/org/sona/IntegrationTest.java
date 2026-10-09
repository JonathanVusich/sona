package org.sona;


import org.junit.jupiter.api.extension.ExtendWith;
import org.sona.config.TestAuthConfig;
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
 * filesystem, stubbed MusicBrainz responses and a fake OIDC provider. The server listens on a random port, so
 * requests pass through the real security filters. Writes commit as they would in production, and the tables are
 * truncated after each test.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfig.class, TestLibraryConfig.class, TestAuthConfig.class})
@ExtendWith(TruncateTablesExtension.class)
public @interface IntegrationTest {
}
