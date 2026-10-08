import { A } from "@solidjs/router";
import { For, Show } from "solid-js";
import { formatTime } from "../format";
import { contextOf, type Track } from "../library";
import { current, playing } from "../player";
import { removeFromPlaylist } from "../playlists";
import { MoreIcon, PlayIcon } from "./Icons";
import Menu from "./Menu";
import { AddToPlaylistItems } from "./PlaylistDialog";

// Rows play on click. With showContext each row also names and links its artist and release, for lists that mix
// releases such as playlists. Passing playlistId offers removing tracks from that playlist.
export default function TrackList(props: {
  tracks: Track[];
  showContext: boolean;
  playlistId?: string;
  onPlay: (index: number) => void;
}) {
  return (
    <ol class="tracklist" classList={{ "with-context": props.showContext }}>
      <For each={props.tracks}>
        {(track, index) => {
          const context = () => contextOf(track);
          const isCurrent = () => current()?.id === track.id;
          // Links and buttons inside the row do their own thing.
          const onRowClick = (event: MouseEvent) => {
            if (!(event.target as Element).closest("a, button, .menu")) {
              props.onPlay(index());
            }
          };
          return (
            <li class="track-row" classList={{ current: isCurrent() }} onClick={onRowClick}>
              <button class="track-play" aria-label={`Play ${track.title}`} onClick={() => props.onPlay(index())}>
                <Show
                  when={isCurrent() && playing()}
                  fallback={<span class="track-number">{props.showContext ? index() + 1 : track.number}</span>}
                >
                  <span class="bars" aria-hidden="true">
                    <span />
                    <span />
                    <span />
                  </span>
                </Show>
                <span class="track-play-icon">
                  <PlayIcon size={14} />
                </span>
              </button>
              <span class="track-main">
                <span class="track-title">{track.title}</span>
                <Show when={props.showContext && context().artist}>
                  {(artist) => (
                    <A href={`/artists/${artist().id}`} class="track-sub">
                      {artist().name}
                    </A>
                  )}
                </Show>
              </span>
              <Show when={props.showContext}>
                <span class="track-release">
                  <Show when={context().release}>
                    {(release) => <A href={`/releases/${release().id}`}>{release().title}</A>}
                  </Show>
                </span>
              </Show>
              <span class="track-duration">{formatTime(track.duration)}</span>
              <Menu label={`More for ${track.title}`} icon={<MoreIcon size={18} />} class="track-menu">
                <AddToPlaylistItems trackIds={() => [track.id]} />
                <hr class="menu-divider" />
                <Show when={context().artist}>
                  {(artist) => (
                    <A href={`/artists/${artist().id}`} class="menu-item" role="menuitem">
                      Go to artist
                    </A>
                  )}
                </Show>
                <Show when={props.showContext && context().release}>
                  {(release) => (
                    <A href={`/releases/${release().id}`} class="menu-item" role="menuitem">
                      Go to release
                    </A>
                  )}
                </Show>
                <Show when={props.playlistId}>
                  {(playlistId) => (
                    <>
                      <hr class="menu-divider" />
                      <button
                        class="menu-item danger"
                        role="menuitem"
                        onClick={() => removeFromPlaylist(playlistId(), track.id)}
                      >
                        Remove from this playlist
                      </button>
                    </>
                  )}
                </Show>
              </Menu>
            </li>
          );
        }}
      </For>
    </ol>
  );
}
