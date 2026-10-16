package org.sona.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * A release's page: its details, and its tracks disc by disc.
 */
@RequiredArgsConstructor
public final class ReleasePage {

    private final Page page;

    public void open(final UUID releaseId) {
        page.navigate("/releases/" + releaseId);
    }

    public Locator title() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1));
    }

    public Locator details() {
        return page.locator(".detail-meta").first();
    }

    public Locator link(final String name) {
        return page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    public Locator disc(final String name) {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(name).setLevel(2));
    }

    public Locator track(final String name) {
        return page.getByRole(AriaRole.LISTITEM).filter(new Locator.FilterOptions().setHasText(name));
    }

    /**
     * @return the link to the artist named on the track's row, which only names an artist who isn't the release's
     */
    public Locator trackArtist(final String trackName, final String artistName) {
        return track(trackName).getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(artistName));
    }

    public Locator message() {
        return page.locator(".empty");
    }
}
