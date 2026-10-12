package org.sona.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import lombok.RequiredArgsConstructor;

/**
 * The settings menu in the top bar, which names the user and holds the account actions.
 */
@RequiredArgsConstructor
public final class SettingsMenu {

    private final Page page;

    public void openMenu() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Settings")).click();
    }

    public void logOut() {
        openMenu();
        menuItem("Log out").click();
    }

    public Locator header() {
        return page.locator(".menu-header");
    }

    public Locator menuItem(final String name) {
        return page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(name));
    }
}
