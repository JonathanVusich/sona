package org.sona.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Page;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.auth.TestUsers;
import org.sona.db.PasswordDao;
import org.sona.db.UserDao;
import org.sona.model.enums.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

@E2ETest
class LoginE2ETest {

    @Autowired
    UserDao userDao;

    @Autowired
    PasswordDao passwordDao;

    @Autowired
    PasswordEncoder encoder;

    TestUsers users;

    @BeforeEach
    void setUp() {
        users = new TestUsers(userDao, passwordDao, encoder);
    }

    @Test
    void aSignedOutVisitorIsSentToTheLoginPage(final Page page) {
        page.navigate("/albums");

        assertThat(page).hasURL(Pattern.compile("/login$"));
    }

    @Test
    void aUserLogsInAndOut(final Page page) {
        users.createUser("alice", UserRole.USER);
        final var menu = new SettingsMenu(page);

        new LoginPage(page).logIn("alice", "alice");

        assertThat(page).hasURL(Pattern.compile("/discover$"));
        menu.openMenu();
        assertThat(menu.header()).containsText("alice");

        menu.menuItem("Log out").click();

        assertThat(page).hasURL(Pattern.compile("/login$"));
        page.reload();
        assertThat(page).hasURL(Pattern.compile("/login$"));
    }

    @Test
    void aBlankUsernameIsRefused(final Page page) {
        users.createUser("alice", UserRole.USER);
        final var login = new LoginPage(page);

        login.logIn(" ", "alice");

        assertThat(login.error()).hasText("Enter your username and password.");
        assertThat(page).hasURL(Pattern.compile("/login$"));
    }

    @Test
    void aWrongPasswordIsRefused(final Page page) {
        users.createUser("alice", UserRole.USER);
        final var login = new LoginPage(page);

        login.logIn("alice", "wrong");

        assertThat(login.error()).hasText("The username or password is incorrect.");
        assertThat(page).hasURL(Pattern.compile("/login$"));
    }
}
