package org.sona.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * One of the user's playlists: its details, its actions, and its tracks in order, which can be dragged into a new order.
 */
@RequiredArgsConstructor
public final class PlaylistPage {

    private final Page page;

    public void open(final UUID playlistId) {
        page.navigate("/playlists/" + playlistId);
    }

    public Locator title() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1));
    }

    public Locator description() {
        return page.locator(".detail-artist");
    }

    public Locator details() {
        return page.locator(".detail-meta");
    }

    /**
     * @return one of the buttons under the playlist's details, such as Play, Shuffle or More
     */
    public Locator actionButton(final String name) {
        return page.locator(".detail-actions")
                .getByRole(
                        AriaRole.BUTTON,
                        new Locator.GetByRoleOptions().setName(name).setExact(true));
    }

    public Locator menuItem(final String name) {
        return page.getByRole(
                AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    /**
     * @return a button of the dialog the page has open, such as the one confirming the playlist's deletion
     */
    public Locator dialogButton(final String name) {
        return page.getByRole(AriaRole.DIALOG)
                .getByRole(
                        AriaRole.BUTTON,
                        new Locator.GetByRoleOptions().setName(name).setExact(true));
    }

    public Locator track(final String name) {
        return page.getByRole(AriaRole.LISTITEM).filter(new Locator.FilterOptions().setHasText(name));
    }

    public Locator trackTitles() {
        return page.locator(".track-title");
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

    /**
     * Drags the track onto another, so it takes that track's place.
     */
    public void dragTrack(final String trackName, final String ontoTrackName) {
        track(trackName).dragTo(track(ontoTrackName));
    }

    /**
     * @return what the page says in place of its tracks, or of the playlist
     */
    public Locator message() {
        return page.locator(".empty");
    }
}
