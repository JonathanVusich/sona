import { useNavigate, useParams } from "@solidjs/router";
import { createSignal, Show } from "solid-js";
import { Mosaic } from "../components/Cover";
import { ConfirmDialog } from "../components/Dialog";
import { MoreIcon, PlayIcon, ShuffleIcon } from "../components/Icons";
import Menu from "../components/Menu";
import PageLayout from "../components/PageLayout";
import { openEditPlaylistDialog } from "../components/PlaylistDialog";
import TrackList, { trackRow } from "../components/TrackList";
import { formatTotal, plural } from "../format";
import { tracksOf } from "../library";
import { playListedTrack, playTracks, shufflePlay } from "../player";
import { deletePlaylist, playlistById } from "../playlists";
import { coverSeeds } from "./Playlists";
import { AppRoutes } from "../routes";

// Whether the page is asking to confirm deleting the playlist.
const PlaylistAction = {
  VIEWING: "viewing",
  CONFIRMING_DELETE: "confirming-delete",
} as const;

type PlaylistAction = (typeof PlaylistAction)[keyof typeof PlaylistAction];

export default function PlaylistDetail() {
  const params = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [action, setAction] = createSignal<PlaylistAction>(PlaylistAction.VIEWING);

  return (
    <Show when={playlistById(params.id)} fallback={<PageLayout header={<p class="empty">This playlist doesn't exist.</p>} />}>
      {(playlist) => {
        const playlistTracks = () => tracksOf(playlist().trackIds);
        const total = () => playlistTracks().reduce((sum, track) => sum + track.duration, 0);
        const remove = () => {
          const id = playlist().id;
          navigate(AppRoutes.PLAYLISTS);
          deletePlaylist(id);
        };
        return (
          <PageLayout
            header={
              <header class="detail-header">
                <Mosaic seeds={coverSeeds(playlist())} class="detail-cover" />
                <div class="detail-info">
                  <span class="eyebrow">Playlist</span>
                  <h1>{playlist().name}</h1>
                  <Show when={playlist().description}>
                    <p class="detail-artist">{playlist().description}</p>
                  </Show>
                  <p class="detail-meta">
                    {plural(playlistTracks().length, "song")} · {formatTotal(total())}
                  </p>
                  <div class="detail-actions">
                    <button class="primary" disabled={playlistTracks().length === 0} onClick={() => playTracks(playlistTracks())}>
                      <PlayIcon size={16} /> Play
                    </button>
                    <button disabled={playlistTracks().length === 0} onClick={() => shufflePlay(playlistTracks())}>
                      <ShuffleIcon size={16} /> Shuffle
                    </button>
                    <Menu label="More" icon={<MoreIcon size={18} />}>
                      <button class="menu-item" role="menuitem" onClick={() => openEditPlaylistDialog(playlist())}>
                        Edit details
                      </button>
                      <button
                        class="menu-item danger"
                        role="menuitem"
                        onClick={() => setAction(PlaylistAction.CONFIRMING_DELETE)}
                      >
                        Delete playlist
                      </button>
                    </Menu>
                  </div>
                </div>
              </header>
            }
          >
            <Show
              when={playlistTracks().length > 0}
              fallback={<p class="empty">Add songs from any album with the ⋯ button on a track.</p>}
            >
              <TrackList
                tracks={playlistTracks().map(trackRow)}
                showContext
                playlistId={playlist().id}
                onPlay={(index) => playListedTrack(playlistTracks(), index)}
              />
            </Show>
            <Show when={action() === PlaylistAction.CONFIRMING_DELETE}>
              <ConfirmDialog
                title="Delete playlist?"
                message={`"${playlist().name}" will be deleted. The songs stay in the library.`}
                confirmLabel="Delete"
                onConfirm={remove}
                onClose={() => setAction(PlaylistAction.VIEWING)}
              />
            </Show>
          </PageLayout>
        );
      }}
    </Show>
  );
}
