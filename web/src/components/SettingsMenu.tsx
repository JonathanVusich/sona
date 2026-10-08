import { A, useNavigate } from "@solidjs/router";
import { Show } from "solid-js";
import { can, logout, user } from "../auth";
import { stop } from "../player";
import { GearIcon } from "./Icons";
import Menu from "./Menu";

export default function SettingsMenu() {
  const navigate = useNavigate();

  const signOut = () => {
    stop();
    logout();
    navigate("/login", { replace: true });
  };

  return (
    <Menu label="Settings" icon={<GearIcon />} class="settings-menu">
      <div class="menu-header">
        <span>{user()?.name}</span>
        <span class="badge">{user()?.role}</span>
      </div>
      <Show when={can("write")}>
        <A href="/admin" class="menu-item" role="menuitem">
          Manage library
        </A>
      </Show>
      <A href="/settings" class="menu-item" role="menuitem">
        Settings
      </A>
      <button class="menu-item" role="menuitem" onClick={signOut}>
        Log out
      </button>
    </Menu>
  );
}
