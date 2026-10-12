package org.sona.e2e;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.auth.TestUsers;
import org.sona.db.PasswordDao;
import org.sona.db.UserDao;
import org.sona.model.enums.PasswordState;
import org.sona.model.enums.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@E2ETest
class PasswordChangeE2ETest {

    @Autowired
    UserDao userDao;

    @Autowired
    PasswordDao passwordDao;

    @Autowired
    PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        new TestUsers(userDao, passwordDao, encoder).createUser("carol", UserRole.USER, PasswordState.CHANGE_REQUIRED);
    }

    @Test
    void aUserWhoMustChangeTheirPasswordCanDoNothingElse(final Page page) {
        new LoginPage(page).logIn("carol", "carol");
        assertThat(page).hasURL(Pattern.compile("/change-password$"));

        page.navigate("/albums");

        assertThat(page).hasURL(Pattern.compile("/change-password$"));
    }

    @Test
    void aChangedPasswordLetsTheUserIn(final Page page) {
        final var login = new LoginPage(page);
        login.logIn("carol", "carol");

        new ChangePasswordPage(page).changePassword("carol", "new-password", "new-password");

        assertThat(page).hasURL(Pattern.compile("/discover$"));
        new SettingsMenu(page).logOut();
        login.logIn("carol", "new-password");
        assertThat(page).hasURL(Pattern.compile("/discover$"));
    }

    @Test
    void mismatchedNewPasswordsAreRefused(final Page page) {
        new LoginPage(page).logIn("carol", "carol");
        final var changePassword = new ChangePasswordPage(page);

        changePassword.changePassword("carol", "new-password", "other-password");

        assertThat(changePassword.error()).hasText("The new passwords don't match.");
    }

    @Test
    void aWrongCurrentPasswordIsRefused(final Page page) {
        new LoginPage(page).logIn("carol", "carol");
        final var changePassword = new ChangePasswordPage(page);

        changePassword.changePassword("wrong", "new-password", "new-password");

        assertThat(changePassword.error()).hasText("The current password is incorrect.");
        assertThat(page).hasURL(Pattern.compile("/change-password$"));
    }
}
