package org.sona.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Playwright;

/**
 * Chromium, started once for the whole run and closed by JUnit when the run ends.
 */
public final class RunningBrowser implements AutoCloseable {

    private final Playwright playwright;
    private final Browser browser;

    public RunningBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    public Browser browser() {
        return browser;
    }

    @Override
    public void close() {
        browser.close();
        playwright.close();
    }
}
