import { A } from "@solidjs/router";
import { For, Show } from "solid-js";
import { formatTime } from "../format";
import { contextOf, type Track } from "../library";
import { current, playing } from "../player";
import { removeFromPlaylist } from "../playlists";
import { MoreIcon, PlayIcon } from "./Icons";
import Menu from "./Menu";
import { AddToPlaylistItems } from "./PlaylistDialog";
import { AppRoutes, routeTo } from "../routes";

/** What a row shows and links to. */
export interface TrackRow {
  id: string;
  number: number;
  title: string;
  /** In seconds. */
  duration: number;
  artist: { id: string; name: string } | undefined;
  release: { id: string; title: string } | undefined;
}

export function trackRow(track: Track): TrackRow {
  const { release, artist } = contextOf(track);
  return {
    id: track.id,
    number: track.number,
    title: track.title,
    duration: track.duration,
    artist,
    release,
  };
}

// Rows play on click. With showContext each row also names and links its artist and release, for lists that mix
// releases such as playlists. Without it, a row names its artist only when it isn't the page's (artistId), as on a
// compilation. Passing playlistId offers removing tracks from that playlist.
export default function TrackList(props: {
  tracks: TrackRow[];
  showContext: boolean;
  artistId?: string;
  // Tracks that aren't in the mocked library can't go on its playlists.
  canAddToPlaylist?: boolean;
  playlistId?: string;
  onPlay: (index: number) => void;
}) {
  return (
    <ol class="tracklist" classList={{ "with-context": props.showContext }}>
      <For each={props.tracks}>
        {(track, index) => {
          const isCurrent = () => current()?.id === track.id;
          // Links and buttons inside the row do their own thing.
          const onRowClick = (event: MouseEvent) => {
            if (!(event.target instanceof Element && event.target.closest("a, button, .menu"))) {
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
                <Show when={(props.showContext || track.artist?.id !== props.artistId) && track.artist}>
                  {(artist) => (
                    <A href={routeTo(AppRoutes.ARTIST, artist().id)} class="track-sub">
                      {artist().name}
                    </A>
                  )}
                </Show>
              </span>
              <Show when={props.showContext}>
                <span class="track-release">
                  <Show when={track.release}>
                    {(release) => <A href={routeTo(AppRoutes.RELEASE, release().id)}>{release().title}</A>}
                  </Show>
                </span>
              </Show>
              <span class="track-duration">{formatTime(track.duration)}</span>
              <Menu label={`More for ${track.title}`} icon={<MoreIcon size={18} />} class="track-menu">
                <Show when={props.canAddToPlaylist !== false}>
                  <AddToPlaylistItems trackIds={() => [track.id]} />
                  <hr class="menu-divider" />
                </Show>
                <Show when={track.artist}>
                  {(artist) => (
                    <A href={routeTo(AppRoutes.ARTIST, artist().id)} class="menu-item" role="menuitem">
                      Go to artist
                    </A>
                  )}
                </Show>
                <Show when={props.showContext && track.release}>
                  {(release) => (
                    <A href={routeTo(AppRoutes.RELEASE, release().id)} class="menu-item" role="menuitem">
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
