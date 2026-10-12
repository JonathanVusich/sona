package org.sona.e2e;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sona.IntegrationTest;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An {@link IntegrationTest} that drives the built web frontend in a browser, as a user would: Firefox, unless the
 * e2eTest task is run with {@code -Pe2eBrowser=chromium} or {@code webkit}. Test methods take a Playwright
 * {@code Page}, or a {@code BrowserContext} for several tabs that share cookies. The server answers over HTTPS, and
 * access tokens last ten seconds, so the frontend refreshes them while tests run. Run with
 * {@code ./gradlew :server:e2eTest}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("e2e")
@IntegrationTest
// Over HTTPS, with a self-signed certificate for localhost, because WebKit won't keep the Secure refresh cookie over
// plain HTTP, even on localhost.
@TestPropertySource(properties = {
        "sona.test.access-token-lifetime=10s",
        "server.ssl.key-store=classpath:e2e/localhost.p12",
        "server.ssl.key-store-type=PKCS12",
        "server.ssl.key-store-password=sona-e2e"
})
@ExtendWith(BrowserExtension.class)
public @interface E2ETest {
}
