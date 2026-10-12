package org.sona.e2e;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.auth.TestUsers;
import org.sona.controller.ApiRoutes;
import org.sona.db.PasswordDao;
import org.sona.db.UserDao;
import org.sona.model.enums.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

@E2ETest
class SessionE2ETest {

    @Autowired
    UserDao userDao;

    @Autowired
    PasswordDao passwordDao;

    @Autowired
    PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        new TestUsers(userDao, passwordDao, encoder).createUser("alice", UserRole.USER);
    }

    @Test
    void aReloadKeepsTheSession(final Page page) {
        new LoginPage(page).logIn("alice", "alice");
        assertThat(page).hasURL(Pattern.compile("/discover$"));

        page.navigate("/albums");

        assertThat(page).hasURL(Pattern.compile("/albums$"));
    }

    @Test
    void theAccessTokenIsRefreshedBeforeItExpires(final Page page) {
        new LoginPage(page).logIn("alice", "alice");
        assertThat(page).hasURL(Pattern.compile("/discover$"));

        // Access tokens last ten seconds in these tests, so the frontend refreshes after five.
        final var refresh = page.waitForResponse(response -> response.url().endsWith(ApiRoutes.AUTH_REFRESH), () -> {
        });

        assertThat(refresh.status()).isEqualTo(200);
        page.reload();
        assertThat(page).hasURL(Pattern.compile("/discover$"));
    }

    @Test
    void tabsReloadingTogetherStayLoggedIn(final BrowserContext browser) {
        final var firstTab = browser.newPage();
        final var secondTab = browser.newPage();
        new LoginPage(firstTab).logIn("alice", "alice");
        assertThat(firstTab).hasURL(Pattern.compile("/discover$"));
        secondTab.navigate("/albums");
        assertThat(secondTab).hasURL(Pattern.compile("/albums$"));

        // Both refresh with the same cookie at once. Unless they take turns, Sona takes the second use for a stolen
        // token and ends the session.
        firstTab.evaluate("location.reload()");
        secondTab.evaluate("location.reload()");

        assertThat(firstTab).hasURL(Pattern.compile("/discover$"));
        assertThat(secondTab).hasURL(Pattern.compile("/albums$"));
        firstTab.reload();
        assertThat(firstTab).hasURL(Pattern.compile("/discover$"));
    }

    @Test
    void loggingOutInOneTabLogsOutTheOthers(final BrowserContext browser) {
        final var firstTab = browser.newPage();
        final var secondTab = browser.newPage();
        new LoginPage(firstTab).logIn("alice", "alice");
        assertThat(firstTab).hasURL(Pattern.compile("/discover$"));
        secondTab.navigate("/albums");
        assertThat(secondTab).hasURL(Pattern.compile("/albums$"));

        new SettingsMenu(firstTab).logOut();

        assertThat(firstTab).hasURL(Pattern.compile("/login$"));
        assertThat(secondTab).hasURL(Pattern.compile("/login$"));
    }
}
