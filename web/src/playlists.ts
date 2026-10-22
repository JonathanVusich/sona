import { createSignal } from "solid-js";
import { currentUser } from "./auth";
import { updateById } from "./collections";
import type { TrackResponse } from "./api/responses";
import { trackContext, trackIdByTitle, trackResponse, tracksOf } from "./library";

// Playlists are personal: each belongs to one user, and anyone can make their own, whatever their library permissions.

export interface Playlist {
  id: string;
  owner: string;
  name: string;
  description: string;
  trackIds: string[];
}

/** A playlist's track with the release it's from, as the playlist page lists it. */
export interface PlaylistTrack extends TrackResponse {
  release: { releaseId: string; name: string };
}

/**
 * @returns the playlist's tracks in order, each with its release
 */
export function playlistEntries(playlist: Playlist): PlaylistTrack[] {
  return tracksOf(playlist.trackIds).flatMap((track) => {
    const context = trackContext(track);
    if (context === null) {
      return [];
    }
    const release = context.release;
    return [{ ...trackResponse(track), release: { releaseId: release.id, name: release.title } }];
  });
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
  seedPlaylist("p1", "user", "Late Night", "Slow and quiet, for after midnight.", [
    "Paper Moons",
    "Nightjar",
    "Window Seat",
    "Fog Signal",
    "Quiet Engines",
    "Lantern",
    "Long Night",
  ]),
  seedPlaylist("p2", "user", "Focus", "Steady, few surprises.", [
    "Carrier Wave",
    "Telemetry",
    "Handshake",
    "Mesa at Dusk",
    "Splashdown",
    "Meridian",
  ]),
  seedPlaylist("p3", "user", "Road Trip", "Windows down.", [
    "Afterglow Drive",
    "Northbound",
    "Exit 14",
    "Glass Canyon",
    "Home Stretch",
    "Static Bloom",
    "Harbor Lights",
    "Rearview",
  ]),
]);

export const myPlaylists = () => {
  const current = currentUser();
  if (current === null) {
    return [];
  }
  return playlists().filter((playlist) => playlist.owner === current.username);
};

export const playlistById = (id: string) => myPlaylists().find((playlist) => playlist.id === id);

export function createPlaylist(name: string, description: string, trackIds: string[]): Playlist {
  const owner = currentUser();
  if (owner === null) {
    throw new Error("Sign in to make a playlist.");
  }
  const playlist: Playlist = { id: crypto.randomUUID(), owner: owner.username, name, description, trackIds };
  setPlaylists((all) => [...all, playlist]);
  return playlist;
}

export function updatePlaylist(id: string, name: string, description: string) {
  setPlaylists((all) => updateById(all, id, (playlist) => ({ ...playlist, name, description })));
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
  const added = trackIds.filter(
    (trackId, i) => !playlist.trackIds.includes(trackId) && trackIds.indexOf(trackId) === i,
  );
  setPlaylists((all) => updateById(all, id, (other) => ({ ...other, trackIds: [...other.trackIds, ...added] })));
  return added.length;
}

export function removeFromPlaylist(id: string, trackId: string) {
  setPlaylists((all) =>
    updateById(all, id, (playlist) => ({
      ...playlist,
      trackIds: playlist.trackIds.filter((other) => other !== trackId),
    })),
  );
}
