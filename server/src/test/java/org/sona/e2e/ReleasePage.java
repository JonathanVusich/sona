package org.sona.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * A release's page: its details, its tracks disc by disc, its group's other editions, and the artist's other release
 * groups.
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
        return page.getByRole(
                AriaRole.LINK, new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    public Locator disc(final String name) {
        return page.getByRole(
                AriaRole.HEADING, new Page.GetByRoleOptions().setName(name).setLevel(2));
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

    /**
     * @return one of the buttons under the release's details, such as Play or Shuffle
     */
    public Locator actionButton(final String name) {
        return page.locator(".detail-actions")
                .getByRole(
                        AriaRole.BUTTON,
                        new Locator.GetByRoleOptions().setName(name).setExact(true));
    }

    public Locator trackPlayButton(final String trackName) {
        return page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Play " + trackName).setExact(true));
    }

    public void openTrackMenu(final String trackName) {
        track(trackName).hover();
        page.getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions()
                                .setName("More for " + trackName)
                                .setExact(true))
                .click();
    }

    public Locator menuItem(final String name) {
        return page.getByRole(
                AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    public Locator editionTitles() {
        return page.locator(".edition-title");
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
