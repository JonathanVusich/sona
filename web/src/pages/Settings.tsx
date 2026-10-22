import { createSignal } from "solid-js";
import { currentUser, PERMISSION_LABELS } from "../auth";
import { NarrowPage, PageBody, PageHead } from "../components/PageLayout";
import { PageHeader } from "../components/Section";
import { applyTheme, loadTheme, parseTheme, Theme } from "../theme";

export default function Settings() {
  const [theme, setTheme] = createSignal<Theme>(loadTheme());

  const changeTheme = (next: Theme) => {
    setTheme(next);
    applyTheme(next);
  };

  return (
    <NarrowPage>
      <PageHead>
        <PageHeader title="Settings" />
      </PageHead>
      <PageBody>
        <div class="settings-group">
          <h3>Account</h3>
          <div class="settings-row">
            <span>Username</span>
            <span class="settings-value">{currentUser()?.username}</span>
          </div>
          <div class="settings-row">
            <span>Permissions</span>
            <span class="settings-value">
              {currentUser()
                ?.permissions.map((permission) => PERMISSION_LABELS[permission])
                .join(", ")}
            </span>
          </div>
        </div>

        <div class="settings-group">
          <h3>Appearance</h3>
          <label class="settings-row">
            <span>Theme</span>
            <select
              class="settings-value"
              value={theme()}
              onChange={(e) => changeTheme(parseTheme(e.currentTarget.value))}
            >
              <option value={Theme.SYSTEM}>System</option>
              <option value={Theme.LIGHT}>Light</option>
              <option value={Theme.DARK}>Dark</option>
            </select>
          </label>
        </div>
      </PageBody>
    </NarrowPage>
  );
}
