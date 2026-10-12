import { A } from "@solidjs/router";
import { Show } from "solid-js";
import { Permission } from "../api/responses";
import { currentUser, hasPermission, logOut } from "../auth";
import { stop } from "../player";
import { GearIcon } from "./Icons";
import Menu from "./Menu";
import { AppRoutes } from "../routes";

export default function SettingsMenu() {
  // Signed out, the layout sends the browser to the login page.
  const signOut = async () => {
    stop();
    await logOut();
  };

  return (
    <Menu label="Settings" icon={<GearIcon />} class="settings-menu">
      <div class="menu-header">
        <span>{currentUser()?.username}</span>
        <span class="badge">{currentUser()?.role.toLowerCase()}</span>
      </div>
      <Show when={hasPermission(Permission.LIBRARY_WRITE)}>
        <A href={AppRoutes.ADMIN} class="menu-item" role="menuitem">
          Manage library
        </A>
      </Show>
      <A href={AppRoutes.SETTINGS} class="menu-item" role="menuitem">
        Settings
      </A>
      <button class="menu-item" role="menuitem" onClick={signOut}>
        Log out
      </button>
    </Menu>
  );
}
