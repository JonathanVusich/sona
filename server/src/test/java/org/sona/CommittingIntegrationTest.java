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
 * Like {@link IntegrationTest}, but tests run without a surrounding transaction, so each service call commits on its
 * own and a missing transaction boundary fails. The tables are truncated after each test.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfig.class, TestLibraryConfig.class})
@ExtendWith(TruncateTablesExtension.class)
public @interface CommittingIntegrationTest {
}
