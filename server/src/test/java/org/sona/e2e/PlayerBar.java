package org.sona.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * The player along the bottom of every page: what's playing, its controls, and how far through it is.
 */
public final class PlayerBar {

    private final Page page;
    private final Locator region;

    public PlayerBar(final Page page) {
        this.page = page;
        region = page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Player"));
    }

    public Locator trackTitle() {
        return region.locator(".player-title");
    }

    public Locator artist() {
        return region.locator(".player-artist");
    }

    /**
     * @return the button that plays or pauses, labelled for what pressing it does
     */
    public Locator button(final String name) {
        return region.getByRole(
                AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(name).setExact(true));
    }

    /**
     * @return the position and length, as "0:03 / 0:10"
     */
    public Locator time() {
        return region.locator(".player-time");
    }

    public Locator toast() {
        return page.getByRole(AriaRole.STATUS);
    }
}
