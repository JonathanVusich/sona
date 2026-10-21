package org.sona.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import java.time.Duration;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * The browser the E2E tests drive, named by {@code sona.e2e.browser} (firefox unless the e2eTest task is given another).
 * It runs in Microsoft's Playwright image, so every machine tests the same Linux browsers and none installs them, and
 * the tests drive it through Playwright's server. Started once for the whole run and closed by JUnit when it ends.
 */
public final class RunningBrowser implements AutoCloseable {

    private static final int SERVER_PORT = 3000;

    private final GenericContainer<?> container;
    private final Playwright playwright;
    private final Browser browser;

    public RunningBrowser() {
        // The client and server must be the same Playwright version; the e2eTest task passes the dependency's.
        final var version = System.getProperty("sona.e2e.playwright-version");
        container = playwrightServer(version);
        container.start();
        // The browsers are in the container, so the client mustn't download its own.
        playwright = Playwright.create(
                new Playwright.CreateOptions().setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
        final var endpoint = "ws://" + container.getHost() + ":" + container.getMappedPort(SERVER_PORT) + "/";
        browser = browserType(System.getProperty("sona.e2e.browser", "firefox")).connect(endpoint);
    }

    public Browser browser() {
        return browser;
    }

    @Override
    public void close() {
        browser.close();
        playwright.close();
        container.stop();
    }

    private static GenericContainer<?> playwrightServer(final String version) {
        final var image = DockerImageName.parse("mcr.microsoft.com/playwright:v" + version + "-noble");
        final var command = "npx -y playwright@" + version + " run-server --port " + SERVER_PORT + " --host 0.0.0.0";
        return new GenericContainer<>(image)
                .withCommand("/bin/sh", "-c", command)
                .withExposedPorts(SERVER_PORT)
                // Lets the browser reach the server under test at host.testcontainers.internal.
                .withAccessToHost(true)
                // Chromium runs out of the default 64 MB of shared memory.
                .withSharedMemorySize(1024L * 1024 * 1024)
                .withCreateContainerCmdModifier(
                        create -> create.withUser("pwuser").withWorkingDir("/home/pwuser"))
                .waitingFor(Wait.forLogMessage(".*Listening on.*", 1).withStartupTimeout(Duration.ofMinutes(3)));
    }

    private BrowserType browserType(final String name) {
        return switch (name) {
            case "chromium" -> playwright.chromium();
            case "firefox" -> playwright.firefox();
            case "webkit" -> playwright.webkit();
            default -> throw new IllegalArgumentException("Unknown browser for the E2E tests: " + name);
        };
    }
}
