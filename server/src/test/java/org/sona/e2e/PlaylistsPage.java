package org.sona.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import lombok.RequiredArgsConstructor;

/**
 * The list of the user's playlists, each a card naming it and how many songs it has.
 */
@RequiredArgsConstructor
public final class PlaylistsPage {

    private final Page page;

    public void open() {
        page.navigate("/playlists");
    }

    public Locator playlist(final String name) {
        return page.getByRole(AriaRole.LINK).filter(new Locator.FilterOptions().setHasText(name));
    }

    public Locator playlistTitles() {
        return page.locator(".card-title");
    }

    public Locator newPlaylistButton() {
        return page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("New playlist").setExact(true));
    }

    public Locator message() {
        return page.locator(".empty");
    }
}
