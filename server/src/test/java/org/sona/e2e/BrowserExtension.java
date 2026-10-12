package org.sona.e2e;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Gives each test its own {@link BrowserSession}, and saves a failing test's Playwright trace to build/e2e-traces (open
 * one with {@code npx playwright show-trace <file>}).
 */
public final class BrowserExtension implements ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(BrowserExtension.class);

    @Override
    public boolean supportsParameter(final ParameterContext parameterContext, final ExtensionContext context) {
        final var type = parameterContext.getParameter().getType();
        return type == Page.class || type == BrowserContext.class;
    }

    @Override
    public Object resolveParameter(final ParameterContext parameterContext, final ExtensionContext context) {
        final var browserContext = browserSession(context).browserContext();
        if (parameterContext.getParameter().getType() == Page.class) {
            return browserContext.newPage();
        }
        return browserContext;
    }

    /**
     * @return the test's session, started the first time the test asks for a page or context; JUnit closes it, and
     * with it the context, when the test ends
     */
    private static BrowserSession browserSession(final ExtensionContext context) {
        return context.getStore(NAMESPACE).computeIfAbsent(BrowserSession.class, _ -> {
            final var browser = context.getRoot().getStore(NAMESPACE)
                    .computeIfAbsent(RunningBrowser.class, _ -> new RunningBrowser(), RunningBrowser.class)
                    .browser();
            return new BrowserSession(context, browser);
        }, BrowserSession.class);
    }
}
