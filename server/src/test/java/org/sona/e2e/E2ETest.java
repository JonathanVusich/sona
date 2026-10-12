package org.sona.e2e;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sona.IntegrationTest;
import org.sona.config.TestFrontendConfig;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An {@link IntegrationTest} that drives the built web frontend in Chromium, as a user would. Test methods take a
 * Playwright {@code Page}, or a {@code BrowserContext} for several tabs that share cookies. Access tokens last ten
 * seconds, so the frontend refreshes them while tests run. Run with {@code ./gradlew :server:e2eTest}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("e2e")
@IntegrationTest
@Import(TestFrontendConfig.class)
@TestPropertySource(properties = "sona.test.access-token-lifetime=10s")
@ExtendWith(BrowserExtension.class)
public @interface E2ETest {
}
