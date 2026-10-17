import { createSignal, For, Show } from "solid-js";
import { addToPlaylist, createPlaylist, myPlaylists, updatePlaylist, type Playlist } from "../playlists";
import Dialog from "./Dialog";
import { toast } from "./Toast";

// Creating a playlist puts the given tracks on it, then hands the new playlist on.
interface NewPlaylist {
  trackIds: string[];
  onCreated: (playlist: Playlist) => void;
}

// One dialog for the whole app, opened from anywhere: null while no playlist is being created or edited.
const [newPlaylist, setNewPlaylist] = createSignal<NewPlaylist | null>(null);
const [editedPlaylist, setEditedPlaylist] = createSignal<Playlist | null>(null);

export function openNewPlaylistDialog(trackIds: string[], onCreated: (playlist: Playlist) => void) {
  setNewPlaylist({ trackIds, onCreated });
}

export function openEditPlaylistDialog(playlist: Playlist) {
  setEditedPlaylist(playlist);
}

export default function PlaylistDialog() {
  const closeNew = () => setNewPlaylist(null);
  const closeEdited = () => setEditedPlaylist(null);
  return (
    <>
      <Show when={newPlaylist()}>
        {(request) => (
          <Dialog title="New playlist" onClose={closeNew}>
            <NewPlaylistForm trackIds={request().trackIds} onCreated={request().onCreated} onDone={closeNew} />
          </Dialog>
        )}
      </Show>
      <Show when={editedPlaylist()}>
        {(playlist) => (
          <Dialog title="Edit playlist" onClose={closeEdited}>
            <EditPlaylistForm playlist={playlist()} onDone={closeEdited} />
          </Dialog>
        )}
      </Show>
    </>
  );
}

function NewPlaylistForm(props: { trackIds: string[]; onCreated: (playlist: Playlist) => void; onDone: () => void }) {
  const [name, setName] = createSignal("");
  const [description, setDescription] = createSignal("");

  const submit = (event: SubmitEvent) => {
    event.preventDefault();
    props.onCreated(createPlaylist(name().trim(), description().trim(), props.trackIds));
    props.onDone();
  };

  return (
    <form class="dialog-form" onSubmit={submit}>
      <PlaylistFields name={name()} description={description()} onName={setName} onDescription={setDescription} />
      <PlaylistActions name={name()} submitLabel="Create" onCancel={props.onDone} />
    </form>
  );
}

function EditPlaylistForm(props: { playlist: Playlist; onDone: () => void }) {
  const [name, setName] = createSignal(props.playlist.name);
  const [description, setDescription] = createSignal(props.playlist.description);

  const submit = (event: SubmitEvent) => {
    event.preventDefault();
    updatePlaylist(props.playlist.id, name().trim(), description().trim());
    props.onDone();
  };

  return (
    <form class="dialog-form" onSubmit={submit}>
      <PlaylistFields name={name()} description={description()} onName={setName} onDescription={setDescription} />
      <PlaylistActions name={name()} submitLabel="Save" onCancel={props.onDone} />
    </form>
  );
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

function PlaylistActions(props: { name: string; submitLabel: string; onCancel: () => void }) {
  return (
    <div class="dialog-actions">
      <button type="button" onClick={() => props.onCancel()}>
        Cancel
      </button>
      <button type="submit" class="primary" disabled={props.name.trim() === ""}>
        {props.submitLabel}
      </button>
    </div>
  );
}

// Menu items for adding tracks to one of the user's playlists, or to a new one.
export function AddToPlaylistItems(props: { trackIds: () => string[] }) {
  const add = (playlist: Playlist) => {
    const added = addToPlaylist(playlist.id, props.trackIds());
    if (added === 0) {
      toast(`Already on ${playlist.name}`);
      return;
    }
    toast(`Added to ${playlist.name}`);
  };

  return (
    <>
      <div class="menu-label">Add to playlist</div>
      <For each={myPlaylists()}>
        {(playlist) => (
          <button class="menu-item" role="menuitem" onClick={() => add(playlist)}>
            {playlist.name}
          </button>
        )}
      </For>
      <button
        class="menu-item"
        role="menuitem"
        onClick={() =>
          openNewPlaylistDialog(props.trackIds(), (created) => toast(`Added to ${created.name}`))
        }
      >
        New playlist…
      </button>
    </>
  );
}
