package org.sona.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import lombok.RequiredArgsConstructor;

/**
 * The discover page. Its recently played section lists the tracks the user played, latest first, loading more as they
 * scroll.
 */
@RequiredArgsConstructor
public final class DiscoverPage {

    private final Page page;

    /**
     * Goes to the page from another one without reloading, so the player keeps playing.
     */
    public void openFromMenu() {
        page.getByRole(
                        AriaRole.LINK,
                        new Page.GetByRoleOptions().setName("Discover").setExact(true))
                .click();
    }

    /**
     * @return the title of every recently played track, in order
     */
    public Locator playedTrackTitles() {
        return page.locator(".track-title");
    }

    public Locator playedTrack(final String name) {
        return page.getByRole(AriaRole.LISTITEM).filter(new Locator.FilterOptions().setHasText(name));
    }

    public Locator trackPlayButton(final String trackName) {
        return page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Play " + trackName).setExact(true));
    }

    /**
     * @return what the recently played section says when the user has played nothing
     */
    public Locator playedMessage() {
        return page.getByText("Songs you play show up here.");
    }
}
