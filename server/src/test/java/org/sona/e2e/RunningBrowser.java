package org.sona.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

/**
 * The browser the E2E tests drive, named by {@code sona.e2e.browser} (firefox unless the e2eTest task is given another),
 * started once for the whole run and closed by JUnit when the run ends.
 */
public final class RunningBrowser implements AutoCloseable {

    private final Playwright playwright;
    private final Browser browser;

    public RunningBrowser() {
        playwright = Playwright.create();
        browser = browserType(System.getProperty("sona.e2e.browser", "firefox")).launch();
    }

    public Browser browser() {
        return browser;
    }

    @Override
    public void close() {
        browser.close();
        playwright.close();
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
