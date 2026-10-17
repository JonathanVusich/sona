package org.sona.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import lombok.RequiredArgsConstructor;

/**
 * The albums page: every release group in the library as a card, by name, loading more as the user scrolls.
 */
@RequiredArgsConstructor
public final class AlbumsPage {

    private final Page page;

    public void open() {
        page.navigate("/albums");
    }

    /**
     * @return the title of every card shown, in order
     */
    public Locator titles() {
        return page.locator(".card-title");
    }

    public Locator album(final String title) {
        return page.getByRole(AriaRole.LINK).filter(new Locator.FilterOptions().setHasText(title));
    }

    /**
     * @return what the album's card says under its title: its artist, and its type if it isn't an album
     */
    public Locator subtitle(final String title) {
        return album(title).locator(".card-subtitle");
    }

    /**
     * Scrolls to the last album shown, which brings the end of the list near enough to load the next page.
     */
    public void scrollToEnd() {
        titles().last().scrollIntoViewIfNeeded();
    }

    public Locator message() {
        return page.locator(".empty");
    }
}
