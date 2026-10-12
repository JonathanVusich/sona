package org.sona.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Path;

/**
 * Gives each test its own browser context, a fresh profile pointed at the running server, and saves a failing test's
 * Playwright trace to build/e2e-traces (open one with {@code npx playwright show-trace <file>}).
 */
public final class BrowserExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(BrowserExtension.class);

    @Override
    public void beforeEach(final ExtensionContext context) {
        final var browser = context.getRoot().getStore(NAMESPACE)
                .computeIfAbsent(RunningBrowser.class, _ -> new RunningBrowser(), RunningBrowser.class)
                .browser();
        final var port = SpringExtension.getApplicationContext(context).getEnvironment()
                .getRequiredProperty("local.server.port");
        final var options = new Browser.NewContextOptions().setBaseURL("http://localhost:" + port);
        final var browserContext = browser.newContext(options);
        browserContext.tracing().start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true));
        context.getStore(NAMESPACE).put(BrowserContext.class, browserContext);
    }

    @Override
    public void afterEach(final ExtensionContext context) {
        final var browserContext = context.getStore(NAMESPACE).remove(BrowserContext.class, BrowserContext.class);
        final var traceOptions = context.getExecutionException()
                .map(_ -> new Tracing.StopOptions().setPath(tracePath(context)))
                .orElseGet(Tracing.StopOptions::new);
        browserContext.tracing().stop(traceOptions);
        browserContext.close();
    }

    @Override
    public boolean supportsParameter(final ParameterContext parameterContext, final ExtensionContext context) {
        final var type = parameterContext.getParameter().getType();
        return type == Page.class || type == BrowserContext.class;
    }

    @Override
    public Object resolveParameter(final ParameterContext parameterContext, final ExtensionContext context) {
        final var browserContext = context.getStore(NAMESPACE).get(BrowserContext.class, BrowserContext.class);
        if (parameterContext.getParameter().getType() == Page.class) {
            return browserContext.newPage();
        }
        return browserContext;
    }

    private static Path tracePath(final ExtensionContext context) {
        final var name = context.getRequiredTestClass().getSimpleName() + "-" + context.getRequiredTestMethod().getName();
        return Path.of("build", "e2e-traces", name + ".zip");
    }
}
