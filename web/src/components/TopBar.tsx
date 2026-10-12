import { A, useMatch } from "@solidjs/router";
import { Show } from "solid-js";
import SettingsMenu from "./SettingsMenu";
import { AppRoutes } from "../routes";

export default function TopBar() {
  // Settings and admin are their own places: the section links go, and the logo is the way back to the library.
  const inSettings = useMatch(() => AppRoutes.SETTINGS);
  const inAdmin = useMatch(() => AppRoutes.ADMIN_PAGES);

  return (
    <header class="topbar">
      <A href={AppRoutes.DISCOVER} class="brand" end>
        sona
      </A>
      <Show when={!inSettings() && !inAdmin()}>
        <nav class="nav" aria-label="Sections">
          <A href={AppRoutes.DISCOVER}>Discover</A>
          <A href={AppRoutes.ALBUMS}>Albums</A>
          <A href={AppRoutes.ARTISTS}>Artists</A>
          <A href={AppRoutes.PLAYLISTS}>Playlists</A>
        </nav>
      </Show>
      <Show when={!inSettings()}>
        <SettingsMenu />
      </Show>
    </header>
  );
}
