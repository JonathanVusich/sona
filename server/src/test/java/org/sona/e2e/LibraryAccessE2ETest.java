package org.sona.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sona.auth.TestUsers;
import org.sona.db.UserDao;
import org.sona.model.enums.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

@E2ETest
class LibraryAccessE2ETest {

    @Autowired
    UserDao userDao;

    @Autowired
    PasswordEncoder encoder;

    TestUsers users;

    @BeforeEach
    void setUp() {
        users = new TestUsers(userDao, encoder);
    }

    @Test
    void anAdminManagesTheLibrary(final Page page) {
        users.createUser("root", UserRole.ADMIN);
        new LoginPage(page).logIn("root", "root");
        final var menu = new SettingsMenu(page);

        menu.openMenu();
        menu.menuItem("Manage library").click();

        assertThat(page).hasURL(Pattern.compile("/admin/library$"));
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Library")))
                .isVisible();
    }

    @Test
    void aUserCannotManageTheLibrary(final Page page) {
        users.createUser("alice", UserRole.USER);
        new LoginPage(page).logIn("alice", "alice");
        final var menu = new SettingsMenu(page);

        menu.openMenu();

        assertThat(menu.menuItem("Manage library")).isHidden();
        page.navigate("/admin/library");
        assertThat(page).hasURL(Pattern.compile("/discover$"));
    }
}
