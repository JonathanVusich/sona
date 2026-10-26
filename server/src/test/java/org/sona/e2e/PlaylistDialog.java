package org.sona.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import lombok.RequiredArgsConstructor;

/**
 * The dialog that creates a playlist, or edits one's name and description.
 */
@RequiredArgsConstructor
public final class PlaylistDialog {

    private final Page page;

    public void fill(final String name, final String description) {
        dialog().getByLabel("Name").fill(name);
        dialog().getByLabel("Description").fill(description);
    }

    public void submit(final String label) {
        dialog().getByRole(
                        AriaRole.BUTTON,
                        new Locator.GetByRoleOptions().setName(label).setExact(true))
                .click();
    }

    public Locator dialog() {
        return page.getByRole(AriaRole.DIALOG);
    }
}
