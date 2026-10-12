import { createSignal } from "solid-js";
import { currentUser, PERMISSION_LABELS } from "../auth";
import PageLayout from "../components/PageLayout";
import { PageHeader } from "../components/Section";
import { applyTheme, loadTheme, parseTheme, type Theme } from "../theme";

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
          <span class="settings-value">{currentUser()?.username}</span>
        </div>
        <div class="settings-row">
          <span>Permissions</span>
          <span class="settings-value">{currentUser()?.permissions.map((permission) => PERMISSION_LABELS[permission]).join(", ")}</span>
        </div>
      </div>

      <div class="settings-group">
        <h3>Appearance</h3>
        <label class="settings-row">
          <span>Theme</span>
          <select class="settings-value" value={theme()} onChange={(e) => changeTheme(parseTheme(e.currentTarget.value))}>
            <option value="system">System</option>
            <option value="light">Light</option>
            <option value="dark">Dark</option>
          </select>
        </label>
      </div>
    </PageLayout>
  );
}
