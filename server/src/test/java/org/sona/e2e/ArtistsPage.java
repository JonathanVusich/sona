package org.sona.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import lombok.RequiredArgsConstructor;

/**
 * The artists page: every artist with a release group in the library as a card, by name, loading more as the user
 * scrolls.
 */
@RequiredArgsConstructor
public final class ArtistsPage {

    private final Page page;

    public void open() {
        page.navigate("/artists");
    }

    /**
     * @return the name on every card shown, in order
     */
    public Locator names() {
        return page.locator(".card-title");
    }

    public Locator artist(final String name) {
        return page.getByRole(AriaRole.LINK).filter(new Locator.FilterOptions().setHasText(name));
    }

    /**
     * @return what the artist's card says under their name: how many releases they have
     */
    public Locator subtitle(final String name) {
        return artist(name).locator(".card-subtitle");
    }

    /**
     * Scrolls to the last artist shown, which brings the end of the list near enough to load the next page.
     */
    public void scrollToEnd() {
        names().last().scrollIntoViewIfNeeded();
    }

    public Locator message() {
        return page.locator(".empty");
    }
}
