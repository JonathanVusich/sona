package org.sona.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Tracing;
import java.nio.file.Path;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;

/**
 * One test's browser context, a fresh profile pointed at the running server, traced while the test runs. Its extension closes
 * it when the test ends, and a failing test's trace is saved to build/e2e-traces.
 */
public final class BrowserSession implements AutoCloseable {

    private final ExtensionContext test;
    private final BrowserContext browserContext;

    public BrowserSession(final ExtensionContext test, final Browser browser) {
        this.test = test;
        final var port = SpringExtension.getApplicationContext(test)
                .getEnvironment()
                .getRequiredProperty("local.server.port", Integer.class);
        // The browser runs in a container, which reaches the server under test through Testcontainers.
        Testcontainers.exposeHostPorts(port);
        // The test certificate is self-signed, and issued for localhost.
        final var options = new Browser.NewContextOptions()
                .setBaseURL("https://" + GenericContainer.INTERNAL_HOST_HOSTNAME + ":" + port)
                .setIgnoreHTTPSErrors(true);
        browserContext = browser.newContext(options);
        browserContext
                .tracing()
                .start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true));
    }

    public BrowserContext browserContext() {
        return browserContext;
    }

    @Override
    public void close() {
        final var traceOptions = test.getExecutionException()
                .map(_ -> new Tracing.StopOptions().setPath(tracePath()))
                .orElseGet(Tracing.StopOptions::new);
        browserContext.tracing().stop(traceOptions);
        browserContext.close();
    }

    private Path tracePath() {
        final var name = test.getRequiredTestClass().getSimpleName() + "-"
                + test.getRequiredTestMethod().getName();
        return Path.of("build", "e2e-traces", name + ".zip");
    }
}
