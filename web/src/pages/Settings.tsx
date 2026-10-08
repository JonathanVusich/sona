import { createSignal } from "solid-js";
import { permissionsOf, user } from "../auth";
import PageLayout from "../components/PageLayout";
import { PageHeader } from "../components/Section";
import { applyTheme, loadTheme, type Theme } from "../theme";

export default function Settings() {
  const [theme, setTheme] = createSignal<Theme>(loadTheme());

  const changeTheme = (next: Theme) => {
    setTheme(next);
    applyTheme(next);
  };

  return (
    <PageLayout header={<PageHeader title="Settings" />} narrow>
      <div class="settings-group">
        <h3>Account</h3>
        <div class="settings-row">
          <span>Username</span>
          <span class="settings-value">{user()?.name}</span>
        </div>
        <div class="settings-row">
          <span>Permissions</span>
          <span class="settings-value">{user() ? permissionsOf(user()!.role).join(", ") : ""}</span>
        </div>
      </div>

      <div class="settings-group">
        <h3>Appearance</h3>
        <label class="settings-row">
          <span>Theme</span>
          <select class="settings-value" value={theme()} onChange={(e) => changeTheme(e.currentTarget.value as Theme)}>
            <option value="system">System</option>
            <option value="light">Light</option>
            <option value="dark">Dark</option>
          </select>
        </label>
      </div>
    </PageLayout>
  );
}
