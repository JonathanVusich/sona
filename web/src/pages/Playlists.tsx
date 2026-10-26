import { A, useNavigate } from "@solidjs/router";
import { createResource, For, Show, type JSX } from "solid-js";
import * as api from "../api/client";
import { ResultKind, type ApiResult } from "../api/client";
import type { PlaylistsResponse, PlaylistSummaryResponse } from "../api/responses";
import { sendAsCurrentUser } from "../auth";
import { CoverSize, Mosaic } from "../components/Cover";
import { PageBody, PageHead, PageMessage } from "../components/PageLayout";
import { openNewPlaylistDialog } from "../components/PlaylistDialog";
import { PageAction, PageHeader, PageMeta } from "../components/Section";
import { plural } from "../format";
import { AppRoutes, routeTo } from "../routes";

// The user's own playlists, by name.
export default function Playlists() {
  const [result] = createResource(() => sendAsCurrentUser((accessToken) => api.fetchPlaylists(accessToken)));

  return (
    <Show when={result()} keyed>
      {(loaded) => loadedPage(loaded)}
    </Show>
  );
}

function loadedPage(result: ApiResult<PlaylistsResponse>): JSX.Element {
  switch (result.kind) {
    case ResultKind.OK:
      return <PlaylistsView playlists={result.body.playlists} />;
    case ResultKind.REFUSED:
    case ResultKind.FAILED:
      return <PageMessage message={result.reason} />;
    default: {
      const unknown: never = result;
      throw new Error(`Unknown result: ${JSON.stringify(unknown)}`);
    }
  }
}

function PlaylistsView(props: { playlists: PlaylistSummaryResponse[] }) {
  const navigate = useNavigate();
  const create = () =>
    openNewPlaylistDialog([], (created) => navigate(routeTo(AppRoutes.PLAYLIST, created.playlistId)));

  return (
    <>
      <PageHead>
        <PageHeader title="Playlists">
          <PageMeta>{plural(props.playlists.length, "playlist")}</PageMeta>
          <PageAction>
            <button onClick={create}>New playlist</button>
          </PageAction>
        </PageHeader>
      </PageHead>
      <PageBody>
        <Show when={props.playlists.length > 0} fallback={<p class="empty">You haven't made any playlists yet.</p>}>
          <div class="card-grid">
            <For each={props.playlists}>
              {(playlist) => (
                <A href={routeTo(AppRoutes.PLAYLIST, playlist.playlistId)} class="card-link">
                  <Mosaic seeds={playlist.coverNames} size={CoverSize.CARD} />
                  <div class="card-title">{playlist.name}</div>
                  <div class="card-subtitle">{plural(playlist.trackCount, "song")}</div>
                </A>
              )}
            </For>
          </div>
        </Show>
      </PageBody>
    </>
  );
}
