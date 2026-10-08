import { A, useNavigate } from "@solidjs/router";
import { For, Show } from "solid-js";
import { Mosaic } from "../components/Cover";
import PageLayout from "../components/PageLayout";
import { openPlaylistDialog } from "../components/PlaylistDialog";
import { PageHeader } from "../components/Section";
import { plural } from "../format";
import { contextOf, tracksOf } from "../library";
import { myPlaylists, type Playlist } from "../playlists";

// Titles of the first release groups a playlist draws from, for its mosaic cover.
export function coverSeeds(playlist: Playlist): string[] {
  const titles = tracksOf(playlist.trackIds).map((track) => contextOf(track).group?.title ?? "");
  return [...new Set(titles)].slice(0, 4);
}

export default function Playlists() {
  const navigate = useNavigate();
  const create = () => openPlaylistDialog({ onCreated: (created) => navigate(`/playlists/${created.id}`) });

  return (
    <PageLayout
      header={
        <PageHeader
          title="Playlists"
          meta={plural(myPlaylists().length, "playlist")}
          action={<button onClick={create}>New playlist</button>}
        />
      }
    >
      <Show when={myPlaylists().length > 0} fallback={<p class="empty">You haven't made any playlists yet.</p>}>
        <div class="card-grid">
          <For each={myPlaylists()}>
            {(playlist) => (
              <A href={`/playlists/${playlist.id}`} class="card-link">
                <Mosaic seeds={coverSeeds(playlist)} />
                <div class="card-title">{playlist.name}</div>
                <div class="card-subtitle">{plural(tracksOf(playlist.trackIds).length, "song")}</div>
              </A>
            )}
          </For>
        </div>
      </Show>
    </PageLayout>
  );
}
