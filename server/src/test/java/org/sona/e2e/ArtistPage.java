package org.sona.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * An artist's page: their details, and their release groups in sections by type, each linking to its page.
 */
@RequiredArgsConstructor
public final class ArtistPage {

    private final Page page;

    public void open(final UUID artistId) {
        page.navigate("/artists/" + artistId);
    }

    public Locator name() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1));
    }

    public Locator details() {
        return page.locator(".detail-meta");
    }

    /**
     * @return the title of every section shown, in order
     */
    public Locator sectionTitles() {
        return page.locator(".section-title");
    }

    /**
     * @return the title of every release group in the section, in order
     */
    public Locator releaseGroupTitles(final String section) {
        return page.locator(".section")
                .filter(new Locator.FilterOptions()
                        .setHas(page.getByRole(
                                AriaRole.HEADING,
                                new Page.GetByRoleOptions().setName(section).setExact(true))))
                .locator(".card-title");
    }

    public Locator releaseGroup(final String title) {
        return page.getByRole(AriaRole.LINK).filter(new Locator.FilterOptions().setHasText(title));
    }

    public Locator message() {
        return page.locator(".empty");
    }
}
