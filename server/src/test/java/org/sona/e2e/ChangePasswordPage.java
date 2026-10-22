package org.sona.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import lombok.RequiredArgsConstructor;

/**
 * The page a user who must change their password is held on.
 */
@RequiredArgsConstructor
public final class ChangePasswordPage {

    private final Page page;

    public void changePassword(final String currentPassword, final String newPassword, final String confirmedPassword) {
        passwordField("Current password").fill(currentPassword);
        passwordField("New password").fill(newPassword);
        passwordField("Confirm new password").fill(confirmedPassword);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Change password"))
                .click();
    }

    public Locator error() {
        return page.locator(".error");
    }

    private Locator passwordField(final String label) {
        return page.getByLabel(label, new Page.GetByLabelOptions().setExact(true));
    }
}
