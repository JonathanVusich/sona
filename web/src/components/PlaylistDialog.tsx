import { createSignal, For, Show } from "solid-js";
import { addToPlaylist, createPlaylist, myPlaylists, updatePlaylist, type Playlist } from "../playlists";
import Dialog from "./Dialog";
import { toast } from "./Toast";

interface Request {
  // Edits this playlist; otherwise creates a new one.
  playlist?: Playlist;
  // Tracks to put on a new playlist.
  trackIds?: string[];
  onCreated?: (playlist: Playlist) => void;
}

// One dialog instance for the whole app, opened from anywhere.
const [request, setRequest] = createSignal<Request | null>(null);

export function openPlaylistDialog(next: Request) {
  setRequest(next);
}

export default function PlaylistDialog() {
  return (
    <Dialog
      open={request() !== null}
      title={request()?.playlist ? "Edit playlist" : "New playlist"}
      onClose={() => setRequest(null)}
    >
      <Show when={request()}>{(current) => <PlaylistForm request={current()} onDone={() => setRequest(null)} />}</Show>
    </Dialog>
  );
}

function PlaylistForm(props: { request: Request; onDone: () => void }) {
  const [name, setName] = createSignal(props.request.playlist?.name ?? "");
  const [description, setDescription] = createSignal(props.request.playlist?.description ?? "");

  const submit = (event: SubmitEvent) => {
    event.preventDefault();
    const existing = props.request.playlist;
    if (existing !== undefined) {
      updatePlaylist(existing.id, name().trim(), description().trim());
    } else {
      const created = createPlaylist(name().trim(), description().trim(), props.request.trackIds ?? []);
      props.request.onCreated?.(created);
    }
    props.onDone();
  };

  return (
    <form class="dialog-form" onSubmit={submit}>
      <label>
        Name
        <input value={name()} onInput={(e) => setName(e.currentTarget.value)} required autofocus maxLength={100} />
      </label>
      <label>
        Description
        <input value={description()} onInput={(e) => setDescription(e.currentTarget.value)} placeholder="Optional" maxLength={300} />
      </label>
      <div class="dialog-actions">
        <button type="button" onClick={() => props.onDone()}>
          Cancel
        </button>
        <button type="submit" class="primary" disabled={name().trim() === ""}>
          {props.request.playlist ? "Save" : "Create"}
        </button>
      </div>
    </form>
  );
}

// Menu items for adding tracks to one of the user's playlists, or to a new one.
export function AddToPlaylistItems(props: { trackIds: () => string[] }) {
  const add = (playlist: Playlist) => {
    const added = addToPlaylist(playlist.id, props.trackIds());
    toast(added === 0 ? `Already on ${playlist.name}` : `Added to ${playlist.name}`);
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
          openPlaylistDialog({ trackIds: props.trackIds(), onCreated: (created) => toast(`Added to ${created.name}`) })
        }
      >
        New playlist…
      </button>
    </>
  );
}
