package org.sona.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * A release group's page: its details, its default release's tracks disc by disc, its editions, each linking to its
 * release's page, and the artist's other release groups.
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
        return page.getByRole(
                AriaRole.LINK, new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    public Locator disc(final String name) {
        return page.getByRole(
                AriaRole.HEADING, new Page.GetByRoleOptions().setName(name).setLevel(2));
    }

    public Locator track(final String name) {
        return page.locator(".track-row").filter(new Locator.FilterOptions().setHasText(name));
    }

    public Locator trackPlayButton(final String trackName) {
        return page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Play " + trackName).setExact(true));
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

    /**
     * @return the title of each of the artist's other release groups, in order
     */
    public Locator moreByTitles() {
        return page.locator(".card-title");
    }

    public Locator message() {
        return page.locator(".empty");
    }
}
