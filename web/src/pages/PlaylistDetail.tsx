import { useNavigate, useParams } from "@solidjs/router";
import { createResource, createSignal, Show } from "solid-js";
import * as api from "../api/client";
import { ResultKind, type ApiResult } from "../api/client";
import type { PlaylistDetailsRequest } from "../api/requests";
import { ErrorCode, type PlaylistResponse, type PlaylistTrackResponse } from "../api/responses";
import { sendAsCurrentUser } from "../auth";
import { CoverSize, Mosaic } from "../components/Cover";
import { ConfirmDialog } from "../components/Dialog";
import { PlayIcon, ShuffleIcon } from "../components/Icons";
import Menu, { MenuKind } from "../components/Menu";
import { PageBody, PageHead, PageMessage } from "../components/PageLayout";
import { openEditPlaylistDialog } from "../components/PlaylistDialog";
import { toast } from "../components/Toast";
import { PlaylistTrackList } from "../components/TrackList";
import { formatTotal, plural } from "../format";
import { playListedTrack, playTracks, shufflePlay } from "../player";
import { AppRoutes } from "../routes";

const NOT_FOUND = "This playlist doesn't exist.";

// Whether the page is asking to confirm deleting the playlist.
const PlaylistAction = {
  VIEWING: "viewing",
  CONFIRMING_DELETE: "confirming-delete",
} as const;

type PlaylistAction = (typeof PlaylistAction)[keyof typeof PlaylistAction];

// One of the user's playlists: its tracks in their order, which the user can play, remove and drag into a new order.
// Each change shows at once and is sent to Sona; the playlist is only loaded again if Sona refuses one, as another
// change may have been made since.
export default function PlaylistDetail() {
  const params = useParams<{ id: string }>();
  const [result, { refetch, mutate }] = createResource(
    () => params.id,
    (playlistId) => sendAsCurrentUser((accessToken) => api.fetchPlaylist(accessToken, playlistId)),
  );
  const playlist = (): PlaylistResponse | null => {
    const loaded = result();
    if (loaded === undefined || loaded.kind !== ResultKind.OK) {
      return null;
    }
    return loaded.body;
  };
  const update = (changed: PlaylistResponse) => mutate({ kind: ResultKind.OK, body: changed });

  return (
    <Show when={playlist()} fallback={<FailureMessage result={result()} />}>
      {(loaded) => <PlaylistView playlist={loaded()} onUpdated={update} onRefused={() => refetch()} />}
    </Show>
  );
}

// Why the playlist didn't load, once it has failed to; nothing while it loads.
function FailureMessage(props: { result: ApiResult<PlaylistResponse> | undefined }) {
  const message = () => {
    const loaded = props.result;
    if (loaded === undefined || loaded.kind === ResultKind.OK) {
      return null;
    }
    return failureMessage(loaded);
  };
  return <Show when={message()}>{(shown) => <PageMessage message={shown()} />}</Show>;
}

function failureMessage(result: ApiResult<PlaylistResponse>): string | null {
  switch (result.kind) {
    case ResultKind.OK:
      return null;
    case ResultKind.REFUSED:
      return refusalMessage(result.code, result.reason);
    case ResultKind.FAILED:
      return result.reason;
    default: {
      const unknown: never = result;
      throw new Error(`Unknown result: ${JSON.stringify(unknown)}`);
    }
  }
}

function refusalMessage(code: ErrorCode, reason: string): string {
  // An ID that isn't one of Sona's can't be one of the user's playlists either.
  switch (code) {
    case ErrorCode.PLAYLIST_NOT_FOUND:
    case ErrorCode.INVALID_PARAMETER:
      return NOT_FOUND;
    default:
      return reason;
  }
}

function PlaylistView(props: {
  playlist: PlaylistResponse;
  onUpdated: (playlist: PlaylistResponse) => void;
  onRefused: () => void;
}) {
  const navigate = useNavigate();
  const [action, setAction] = createSignal<PlaylistAction>(PlaylistAction.VIEWING);
  const tracks = () => props.playlist.tracks;
  const total = () => tracks().reduce((sum, track) => sum + track.duration, 0);
  const playlistId = () => props.playlist.playlistId;

  const remove = async () => {
    const result = await sendAsCurrentUser((accessToken) => api.deletePlaylist(accessToken, playlistId()));
    if (result.kind !== ResultKind.OK) {
      toast(result.reason);
      return;
    }
    navigate(AppRoutes.PLAYLISTS);
  };
  const showTracks = (changed: PlaylistTrackResponse[]) => props.onUpdated({ ...props.playlist, tracks: changed });
  const showDetails = (details: PlaylistDetailsRequest) => props.onUpdated({ ...props.playlist, ...details });
  const reportRefusal = (result: ApiResult<void>) => {
    if (result.kind !== ResultKind.OK) {
      toast(result.reason);
      props.onRefused();
    }
  };
  const removeTrack = async (track: PlaylistTrackResponse) => {
    showTracks(tracks().filter((other) => other !== track));
    const result = await sendAsCurrentUser((accessToken) =>
      api.removePlaylistTrack(accessToken, playlistId(), track.trackId),
    );
    reportRefusal(result);
  };
  const moveTrack = async (track: PlaylistTrackResponse, position: number) => {
    const moved = movedTracks(tracks(), track, position);
    if (moved === null) {
      return;
    }
    showTracks(moved);
    const request = { position };
    const result = await sendAsCurrentUser((accessToken) =>
      api.movePlaylistTrack(accessToken, playlistId(), track.trackId, request),
    );
    reportRefusal(result);
  };

  return (
    <>
      <PageHead>
        <header class="detail-header">
          <Mosaic seeds={coverNames(tracks())} size={CoverSize.DETAIL} />
          <div class="detail-info">
            <span class="eyebrow">Playlist</span>
            <h1>{props.playlist.name}</h1>
            <Show when={props.playlist.description}>
              {(description) => <p class="detail-artist">{description()}</p>}
            </Show>
            <p class="detail-meta">
              {plural(tracks().length, "song")} · {formatTotal(total())}
            </p>
            <div class="detail-actions">
              <button class="primary" disabled={tracks().length === 0} onClick={() => playTracks(tracks())}>
                <PlayIcon size={16} /> Play
              </button>
              <button disabled={tracks().length === 0} onClick={() => shufflePlay(tracks())}>
                <ShuffleIcon size={16} /> Shuffle
              </button>
              <Menu kind={MenuKind.ACTIONS} label="More">
                <button
                  class="menu-item"
                  role="menuitem"
                  onClick={() => openEditPlaylistDialog(props.playlist, showDetails)}
                >
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
      </PageHead>
      <PageBody>
        <Show
          when={tracks().length > 0}
          fallback={<p class="empty">Add songs from any album with the ⋯ button on a track.</p>}
        >
          <PlaylistTrackList
            tracks={tracks()}
            onPlay={(index) => playListedTrack(tracks(), index)}
            onRemove={removeTrack}
            onMove={moveTrack}
          />
        </Show>
        <Show when={action() === PlaylistAction.CONFIRMING_DELETE}>
          <ConfirmDialog
            title="Delete playlist?"
            message={`"${props.playlist.name}" will be deleted. The songs stay in the library.`}
            confirmLabel="Delete"
            onConfirm={remove}
            onClose={() => setAction(PlaylistAction.VIEWING)}
          />
        </Show>
      </PageBody>
    </>
  );
}

/**
 * @returns the tracks with the track moved to the position, from 1, as Sona moves it; null when it's already there
 */
function movedTracks(
  tracks: PlaylistTrackResponse[],
  track: PlaylistTrackResponse,
  position: number,
): PlaylistTrackResponse[] | null {
  if (tracks.indexOf(track) === position - 1) {
    return null;
  }
  const others = tracks.filter((other) => other !== track);
  return [...others.slice(0, position - 1), track, ...others.slice(position - 1)];
}

// The playlist page has its tracks' releases, not their release groups, so its cover is drawn from the first
// releases' names, which are mostly their groups'.
function coverNames(tracks: PlaylistTrackResponse[]): string[] {
  const names = tracks.map((track) => track.release.name);
  return [...new Set(names)].slice(0, 4);
}
