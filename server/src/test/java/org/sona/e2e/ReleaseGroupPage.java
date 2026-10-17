package org.sona.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * A release group's page: its details, and its editions, each linking to its release's page.
 */
@RequiredArgsConstructor
public final class ReleaseGroupPage {

    private final Page page;

    public void open(final UUID releaseGroupId) {
        page.navigate("/release-groups/" + releaseGroupId);
    }

    public Locator title() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1));
    }

    public Locator type() {
        return page.locator(".eyebrow");
    }

    public Locator details() {
        return page.locator(".detail-meta");
    }

    public Locator link(final String name) {
        return page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    /**
     * @return every edition's row, in order
     */
    public Locator editions() {
        return page.locator(".edition");
    }

    public Locator editionTitles() {
        return page.locator(".edition-title");
    }

    public Locator editionDetails() {
        return page.locator(".edition-details");
    }

    public Locator message() {
        return page.locator(".empty");
    }
}
