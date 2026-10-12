package org.sona.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import lombok.RequiredArgsConstructor;

/**
 * The frontend's login page.
 */
@RequiredArgsConstructor
public final class LoginPage {

    private final Page page;

    public void logIn(final String username, final String password) {
        page.navigate("/login");
        page.getByLabel("Username").fill(username);
        page.getByLabel("Password").fill(password);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Log in")).click();
    }

    public Locator error() {
        return page.locator(".error");
    }
}
