import { createSignal } from "solid-js";
import { user } from "./auth";
import { trackIdByTitle } from "./library";

// Playlists are personal: each belongs to one user, and anyone can make their own, whatever their library permissions.

export interface Playlist {
  id: string;
  owner: string;
  name: string;
  description: string;
  trackIds: string[];
}

function seedPlaylist(id: string, owner: string, name: string, description: string, titles: string[]): Playlist {
  return {
    id,
    owner,
    name,
    description,
    trackIds: titles.map(trackIdByTitle).filter((trackId): trackId is string => trackId !== undefined),
  };
}

const [playlists, setPlaylists] = createSignal<Playlist[]>([
  seedPlaylist("p1", "user", "Late Night", "Slow and quiet, for after midnight.", ["Paper Moons", "Nightjar", "Window Seat", "Fog Signal", "Quiet Engines", "Lantern", "Long Night"]),
  seedPlaylist("p2", "user", "Focus", "Steady, few surprises.", ["Carrier Wave", "Telemetry", "Handshake", "Mesa at Dusk", "Splashdown", "Meridian"]),
  seedPlaylist("p3", "user", "Road Trip", "Windows down.", ["Afterglow Drive", "Northbound", "Exit 14", "Glass Canyon", "Home Stretch", "Static Bloom", "Harbor Lights", "Rearview"]),
]);

export const myPlaylists = () => {
  const current = user();
  return current === null ? [] : playlists().filter((playlist) => playlist.owner === current.name);
};

export const playlistById = (id: string) => myPlaylists().find((playlist) => playlist.id === id);

export function createPlaylist(name: string, description: string, trackIds: string[]): Playlist {
  const playlist: Playlist = { id: crypto.randomUUID(), owner: user()!.name, name, description, trackIds };
  setPlaylists((all) => [...all, playlist]);
  return playlist;
}

export function updatePlaylist(id: string, name: string, description: string) {
  setPlaylists((all) => all.map((playlist) => (playlist.id === id ? { ...playlist, name, description } : playlist)));
}

export function deletePlaylist(id: string) {
  setPlaylists((all) => all.filter((playlist) => playlist.id !== id));
}

// Skips tracks already on the playlist; returns how many were added.
export function addToPlaylist(id: string, trackIds: string[]): number {
  const playlist = playlistById(id);
  if (playlist === undefined) {
    return 0;
  }
  const added = trackIds.filter((trackId, i) => !playlist.trackIds.includes(trackId) && trackIds.indexOf(trackId) === i);
  setPlaylists((all) => all.map((other) => (other.id === id ? { ...other, trackIds: [...other.trackIds, ...added] } : other)));
  return added.length;
}

export function removeFromPlaylist(id: string, trackId: string) {
  setPlaylists((all) =>
    all.map((playlist) =>
      playlist.id === id ? { ...playlist, trackIds: playlist.trackIds.filter((other) => other !== trackId) } : playlist,
    ),
  );
}
