import { createResource, createSignal, For, Show } from "solid-js";
import * as api from "../api/client";
import { ResultKind, type ApiResult } from "../api/client";
import type { AddedTracksResponse, PlaylistResponse, PlaylistSummaryResponse } from "../api/responses";
import { sendAsCurrentUser } from "../auth";
import { failureReason, FormStatus, isBlank } from "../forms";
import Dialog from "./Dialog";
import FormError from "./FormError";
import { toast } from "./Toast";

/** A playlist just created from the new playlist dialog. */
export interface CreatedPlaylist {
  playlistId: string;
  name: string;
}

// Creating a playlist puts the given tracks on it, then hands the new playlist on.
interface NewPlaylist {
  trackIds: string[];
  onCreated: (playlist: CreatedPlaylist) => void;
}

// Saving a playlist's details tells the page showing it to load it again.
interface EditedPlaylist {
  playlist: PlaylistResponse;
  onSaved: () => void;
}

// One dialog for the whole app, opened from anywhere: null while no playlist is being created or edited.
const [newPlaylist, setNewPlaylist] = createSignal<NewPlaylist | null>(null);
const [editedPlaylist, setEditedPlaylist] = createSignal<EditedPlaylist | null>(null);

export function openNewPlaylistDialog(trackIds: string[], onCreated: (playlist: CreatedPlaylist) => void) {
  setNewPlaylist({ trackIds, onCreated });
}

export function openEditPlaylistDialog(playlist: PlaylistResponse, onSaved: () => void) {
  setEditedPlaylist({ playlist, onSaved });
}

export default function PlaylistDialog() {
  const closeNew = () => setNewPlaylist(null);
  const closeEdited = () => setEditedPlaylist(null);
  return (
    <>
      <Show when={newPlaylist()}>
        {(request) => (
          <Dialog title="New playlist" onClose={closeNew}>
            <NewPlaylistForm request={request()} onDone={closeNew} />
          </Dialog>
        )}
      </Show>
      <Show when={editedPlaylist()}>
        {(edited) => (
          <Dialog title="Edit playlist" onClose={closeEdited}>
            <EditPlaylistForm edited={edited()} onDone={closeEdited} />
          </Dialog>
        )}
      </Show>
    </>
  );
}

function NewPlaylistForm(props: { request: NewPlaylist; onDone: () => void }) {
  const [name, setName] = createSignal("");
  const [description, setDescription] = createSignal("");
  const [error, setError] = createSignal<string | null>(null);
  const [status, setStatus] = createSignal<FormStatus>(FormStatus.EDITING);

  const submit = async (event: SubmitEvent) => {
    event.preventDefault();
    setStatus(FormStatus.SUBMITTING);
    const request = {
      name: name().trim(),
      description: descriptionRequest(description()),
      trackIds: props.request.trackIds,
    };
    const result = await sendAsCurrentUser((accessToken) => api.createPlaylist(accessToken, request));
    setStatus(FormStatus.EDITING);
    if (result.kind !== ResultKind.OK) {
      setError(result.reason);
      return;
    }
    props.request.onCreated({ playlistId: result.body.playlistId, name: request.name });
    props.onDone();
  };

  return (
    <form class="dialog-form" onSubmit={submit}>
      <PlaylistFields name={name()} description={description()} onName={setName} onDescription={setDescription} />
      <FormError error={error()} />
      <PlaylistActions name={name()} status={status()} submitLabel="Create" onCancel={props.onDone} />
    </form>
  );
}

function EditPlaylistForm(props: { edited: EditedPlaylist; onDone: () => void }) {
  const [name, setName] = createSignal(props.edited.playlist.name);
  const [description, setDescription] = createSignal(props.edited.playlist.description ?? "");
  const [error, setError] = createSignal<string | null>(null);
  const [status, setStatus] = createSignal<FormStatus>(FormStatus.EDITING);

  const submit = async (event: SubmitEvent) => {
    event.preventDefault();
    setStatus(FormStatus.SUBMITTING);
    const playlistId = props.edited.playlist.playlistId;
    const request = { name: name().trim(), description: descriptionRequest(description()) };
    const result = await sendAsCurrentUser((accessToken) => api.updatePlaylist(accessToken, playlistId, request));
    setStatus(FormStatus.EDITING);
    const reason = failureReason(result);
    if (reason !== null) {
      setError(reason);
      return;
    }
    props.edited.onSaved();
    props.onDone();
  };

  return (
    <form class="dialog-form" onSubmit={submit}>
      <PlaylistFields name={name()} description={description()} onName={setName} onDescription={setDescription} />
      <FormError error={error()} />
      <PlaylistActions name={name()} status={status()} submitLabel="Save" onCancel={props.onDone} />
    </form>
  );
}

// A blank description is none at all.
function descriptionRequest(description: string): string | null {
  if (isBlank(description)) {
    return null;
  }
  return description.trim();
}

function PlaylistFields(props: {
  name: string;
  description: string;
  onName: (name: string) => void;
  onDescription: (description: string) => void;
}) {
  return (
    <>
      <label>
        Name
        <input
          value={props.name}
          onInput={(e) => props.onName(e.currentTarget.value)}
          required
          autofocus
          maxLength={100}
        />
      </label>
      <label>
        Description
        <input
          value={props.description}
          onInput={(e) => props.onDescription(e.currentTarget.value)}
          placeholder="Optional"
          maxLength={300}
        />
      </label>
    </>
  );
}

function PlaylistActions(props: { name: string; status: FormStatus; submitLabel: string; onCancel: () => void }) {
  return (
    <div class="dialog-actions">
      <button type="button" onClick={() => props.onCancel()}>
        Cancel
      </button>
      <button type="submit" class="primary" disabled={isBlank(props.name) || props.status === FormStatus.SUBMITTING}>
        {props.submitLabel}
      </button>
    </div>
  );
}

// Menu items for adding tracks to one of the user's playlists, or to a new one. The playlists load as the menu opens,
// and are left out while they load or if they fail to.
export function AddToPlaylistItems(props: { trackIds: string[] }) {
  const [result] = createResource(() => sendAsCurrentUser((accessToken) => api.fetchPlaylists(accessToken)));
  const playlists = (): PlaylistSummaryResponse[] => {
    const loaded = result();
    if (loaded === undefined || loaded.kind !== ResultKind.OK) {
      return [];
    }
    return loaded.body.playlists;
  };

  return (
    <>
      <div class="menu-label">Add to playlist</div>
      <For each={playlists()}>
        {(playlist) => (
          <button class="menu-item" role="menuitem" onClick={() => addTracks(playlist, props.trackIds)}>
            {playlist.name}
          </button>
        )}
      </For>
      <button
        class="menu-item"
        role="menuitem"
        onClick={() => openNewPlaylistDialog(props.trackIds, (created) => toast(`Added to ${created.name}`))}
      >
        New playlist…
      </button>
    </>
  );
}

async function addTracks(playlist: PlaylistSummaryResponse, trackIds: string[]) {
  const request = { trackIds };
  const result = await sendAsCurrentUser((accessToken) =>
    api.addPlaylistTracks(accessToken, playlist.playlistId, request),
  );
  toast(addedMessage(playlist, result));
}

function addedMessage(playlist: PlaylistSummaryResponse, result: ApiResult<AddedTracksResponse>): string {
  if (result.kind !== ResultKind.OK) {
    return result.reason;
  }
  if (result.body.added === 0) {
    return `Already on ${playlist.name}`;
  }
  return `Added to ${playlist.name}`;
}
