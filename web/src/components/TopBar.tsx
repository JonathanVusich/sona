import { A, useMatch } from "@solidjs/router";
import { Show } from "solid-js";
import SettingsMenu from "./SettingsMenu";

export default function TopBar() {
  // Settings and admin are their own places: the section links go, and the logo is the way back to the library.
  const inSettings = useMatch(() => "/settings");
  const inAdmin = useMatch(() => "/admin/*");

  return (
    <header class="topbar">
      <A href="/discover" class="brand" end>
        sona
      </A>
      <Show when={!inSettings() && !inAdmin()}>
        <nav class="nav" aria-label="Sections">
          <A href="/discover">Discover</A>
          <A href="/albums">Albums</A>
          <A href="/artists">Artists</A>
          <A href="/playlists">Playlists</A>
        </nav>
      </Show>
      <Show when={!inSettings()}>
        <SettingsMenu />
      </Show>
    </header>
  );
}
