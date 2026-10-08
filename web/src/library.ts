import { createSignal } from "solid-js";
import type { PickedFile } from "./audioFiles";

// Mocked music library held in memory; it resets on reload. Modelled on MusicBrainz: an artist has release groups
// (the album as an idea), a release group has releases (concrete editions), and a release has tracks.

export type ReleaseGroupType = "Album" | "EP" | "Single";

export interface Artist {
  id: string;
  name: string;
  country: string | null;
}

export interface ReleaseGroup {
  id: string;
  artistId: string;
  title: string;
  type: ReleaseGroupType;
  addedAt: number;
  lastPlayedAt: number | null;
}

export interface Release {
  id: string;
  groupId: string;
  title: string;
  year: number | null;
  format: string;
  label: string | null;
  country: string | null;
}

export interface Track {
  id: string;
  releaseId: string;
  number: number;
  title: string;
  // Seconds.
  duration: number;
}

export const FORMATS = ["Digital", "CD", "Vinyl", "Cassette"];

interface SeedRelease {
  // Defaults to the release group's title.
  title?: string;
  year: number;
  format: string;
  label: string;
  country: string;
  // Bonus tracks appended to the release group's songs.
  extra?: string[];
}

interface SeedGroup {
  title: string;
  type: ReleaseGroupType;
  playedHoursAgo: number | null;
  songs: string[];
  releases: SeedRelease[];
}

interface SeedArtist {
  name: string;
  country: string;
  groups: SeedGroup[];
}

const SEED: SeedArtist[] = [
  {
    name: "The Low Tides",
    country: "GB",
    groups: [
      {
        title: "Saltwater",
        type: "Album",
        playedHoursAgo: 1,
        songs: ["Harbor Lights", "Copper Sky", "Undertow", "Breakwater", "Fog Signal", "Salt and Iron", "Low Tide Waltz", "Lighthouse Keeper"],
        releases: [
          { year: 2021, format: "Digital", label: "Harbour Records", country: "GB" },
          { year: 2021, format: "Vinyl", label: "Harbour Records", country: "GB" },
          { title: "Saltwater (Deluxe Edition)", year: 2022, format: "Digital", label: "Harbour Records", country: "GB", extra: ["Undertow (Live)", "Harbor Lights (Acoustic)"] },
        ],
      },
      {
        title: "Harbor Lights",
        type: "Single",
        playedHoursAgo: null,
        songs: ["Harbor Lights", "Harbor Lights (Radio Edit)"],
        releases: [{ year: 2021, format: "Digital", label: "Harbour Records", country: "GB" }],
      },
      {
        title: "Tidepool",
        type: "EP",
        playedHoursAgo: null,
        songs: ["Tidepool", "Anemone", "Rockpool", "Brine"],
        releases: [{ year: 2019, format: "Digital", label: "Harbour Records", country: "GB" }],
      },
      {
        title: "Driftwood",
        type: "Album",
        playedHoursAgo: 400,
        songs: ["Driftwood", "Shoreline", "Gulls", "Pier Lights", "Tidal", "Old Boats"],
        releases: [{ year: 2017, format: "CD", label: "Harbour Records", country: "GB" }],
      },
    ],
  },
  {
    name: "Mira Vale",
    country: "SE",
    groups: [
      {
        title: "Night Garden",
        type: "Album",
        playedHoursAgo: 5,
        songs: ["Paper Moons", "Small Hours", "Nightjar", "Moth Light", "Evening Primrose", "Lantern", "Dew"],
        releases: [
          { year: 2019, format: "Digital", label: "Lumen", country: "SE" },
          { year: 2020, format: "Vinyl", label: "Lumen", country: "SE" },
        ],
      },
      {
        title: "Daybreak",
        type: "Album",
        playedHoursAgo: null,
        songs: ["Daybreak", "Glasshouse", "Morning Tide", "Slow Sun", "Linen", "Awake"],
        releases: [{ year: 2023, format: "Digital", label: "Lumen", country: "SE" }],
      },
      {
        title: "Moth Light",
        type: "Single",
        playedHoursAgo: null,
        songs: ["Moth Light"],
        releases: [{ year: 2019, format: "Digital", label: "Lumen", country: "SE" }],
      },
    ],
  },
  {
    name: "Echo Parade",
    country: "US",
    groups: [
      {
        title: "Signal",
        type: "Album",
        playedHoursAgo: 26,
        songs: ["Static Bloom", "Satellite Hymn", "Carrier Wave", "Interference", "Callsign", "Dead Air", "Handshake", "Relay", "Clear Channel"],
        releases: [
          { year: 2023, format: "Digital", label: "Static House", country: "US" },
          { year: 2023, format: "CD", label: "Static House", country: "US" },
        ],
      },
      {
        title: "Interference",
        type: "EP",
        playedHoursAgo: null,
        songs: ["Interference", "Noise Floor", "Feedback Loop", "Low Pass"],
        releases: [{ year: 2021, format: "Digital", label: "Static House", country: "US" }],
      },
    ],
  },
  {
    name: "Juniper & Ash",
    country: "CA",
    groups: [
      {
        title: "Field Notes",
        type: "Album",
        playedHoursAgo: 70,
        songs: ["Northbound", "Wildfire Season", "Birch", "Trail Marker", "Cold Creek", "Meridian"],
        releases: [{ year: 2018, format: "Digital", label: "Pine & Paper", country: "CA" }],
      },
      {
        title: "Second Growth",
        type: "Album",
        playedHoursAgo: null,
        songs: ["Second Growth", "Seedling", "Canopy", "Understory", "Clearcut", "Old Growth", "Rings"],
        releases: [{ year: 2022, format: "Digital", label: "Pine & Paper", country: "CA" }],
      },
    ],
  },
  {
    name: "Lucas Floyd",
    country: "US",
    groups: [
      {
        title: "Canyon",
        type: "Album",
        playedHoursAgo: null,
        songs: ["Glass Canyon", "Red Rock", "Mesa at Dusk", "Switchback", "Dry Riverbed", "Overlook", "Sandstone"],
        releases: [{ year: 2022, format: "Digital", label: "Mesa Sound", country: "US" }],
      },
      {
        title: "Red Rock",
        type: "Single",
        playedHoursAgo: null,
        songs: ["Red Rock"],
        releases: [{ year: 2022, format: "Digital", label: "Mesa Sound", country: "US" }],
      },
    ],
  },
  {
    name: "Neon Orchard",
    country: "JP",
    groups: [
      {
        title: "Overpass",
        type: "Album",
        playedHoursAgo: null,
        songs: ["Afterglow Drive", "Sodium Lights", "Exit 14", "Merge", "Midnight Lane", "Tunnel Vision", "Home Stretch", "Rearview"],
        releases: [{ year: 2020, format: "Digital", label: "Kōsoku", country: "JP" }],
      },
    ],
  },
  {
    name: "Halcyon Radio",
    country: "DE",
    groups: [
      {
        title: "Transmissions",
        type: "Album",
        playedHoursAgo: null,
        songs: ["Quiet Engines", "Low Orbit", "Telemetry", "Ground Control", "Re-entry", "Splashdown"],
        releases: [{ year: 2024, format: "Digital", label: "Funkhaus", country: "DE" }],
      },
      {
        title: "Telemetry",
        type: "Single",
        playedHoursAgo: null,
        songs: ["Telemetry", "Telemetry (Extended)"],
        releases: [{ year: 2024, format: "Digital", label: "Funkhaus", country: "DE" }],
      },
    ],
  },
  {
    name: "Clara Holm",
    country: "NO",
    groups: [
      {
        title: "Winter Rooms",
        type: "Album",
        playedHoursAgo: 150,
        songs: ["First Frost", "Window Seat", "Radiator Song", "Snowblind", "Thaw", "Woolen", "Kettle", "Long Night"],
        releases: [
          { year: 2017, format: "CD", label: "Fjell", country: "NO" },
          { year: 2018, format: "Vinyl", label: "Fjell", country: "NO" },
        ],
      },
      {
        title: "Thaw",
        type: "Single",
        playedHoursAgo: null,
        songs: ["Thaw"],
        releases: [{ year: 2016, format: "Digital", label: "Fjell", country: "NO" }],
      },
    ],
  },
];

const HOUR = 60 * 60 * 1000;

// Deterministic 2:30 to 5:00, so the same song has the same length on every edition.
function durationFor(title: string): number {
  let hash = 0;
  for (const char of title) {
    hash = (hash * 31 + char.charCodeAt(0)) | 0;
  }
  return 150 + (Math.abs(hash) % 150);
}

function buildSeed(now: number) {
  const artists: Artist[] = [];
  const groups: ReleaseGroup[] = [];
  const releases: Release[] = [];
  const tracks: Track[] = [];
  SEED.forEach((seedArtist, a) => {
    const artistId = `ar${a + 1}`;
    artists.push({ id: artistId, name: seedArtist.name, country: seedArtist.country });
    for (const seedGroup of seedArtist.groups) {
      const groupId = `rg${groups.length + 1}`;
      groups.push({
        id: groupId,
        artistId,
        title: seedGroup.title,
        type: seedGroup.type,
        addedAt: now - (groups.length + 1) * 30 * HOUR,
        lastPlayedAt: seedGroup.playedHoursAgo === null ? null : now - seedGroup.playedHoursAgo * HOUR,
      });
      for (const seedRelease of seedGroup.releases) {
        const releaseId = `r${releases.length + 1}`;
        releases.push({
          id: releaseId,
          groupId,
          title: seedRelease.title ?? seedGroup.title,
          year: seedRelease.year,
          format: seedRelease.format,
          label: seedRelease.label,
          country: seedRelease.country,
        });
        [...seedGroup.songs, ...(seedRelease.extra ?? [])].forEach((title, n) =>
          tracks.push({ id: `${releaseId}t${n + 1}`, releaseId, number: n + 1, title, duration: durationFor(title) }),
        );
      }
    }
  });
  return { artists, groups, releases, tracks: Object.fromEntries(tracks.map((track) => [track.id, track])) };
}

const seed = buildSeed(Date.now());

const [artists, setArtists] = createSignal<Artist[]>(seed.artists);
const [groups, setGroups] = createSignal<ReleaseGroup[]>(seed.groups);
const [releases, setReleases] = createSignal<Release[]>(seed.releases);
const [tracks, setTracks] = createSignal<Record<string, Track>>(seed.tracks);

export { artists, groups, releases };

const collator = new Intl.Collator(undefined, { sensitivity: "base", numeric: true });

// Lookups

export const artistById = (id: string) => artists().find((artist) => artist.id === id);
export const groupById = (id: string) => groups().find((group) => group.id === id);
export const releaseById = (id: string) => releases().find((release) => release.id === id);

// Resolves track ids, dropping any whose release has since been removed.
export const tracksOf = (ids: string[]) =>
  ids.map((id) => tracks()[id]).filter((track): track is Track => track !== undefined);

export const trackCount = () => Object.keys(tracks()).length;

export const tracksOfRelease = (releaseId: string) =>
  Object.values(tracks())
    .filter((track) => track.releaseId === releaseId)
    .sort((a, b) => a.number - b.number);

// Oldest first: the original edition leads.
export const releasesOf = (groupId: string) =>
  releases()
    .filter((release) => release.groupId === groupId)
    .sort((a, b) => (a.year ?? 0) - (b.year ?? 0) || collator.compare(a.title, b.title));

// The release that stands for its group: the earliest edition.
export const canonicalRelease = (groupId: string): Release | undefined => releasesOf(groupId)[0];

export const groupYear = (groupId: string) => canonicalRelease(groupId)?.year ?? null;

// Newest first.
export const groupsOf = (artistId: string) =>
  groups()
    .filter((group) => group.artistId === artistId)
    .sort((a, b) => (groupYear(b.id) ?? 0) - (groupYear(a.id) ?? 0));

export function contextOf(track: Track) {
  const release = releaseById(track.releaseId);
  const group = release === undefined ? undefined : groupById(release.groupId);
  const artist = group === undefined ? undefined : artistById(group.artistId);
  return { release, group, artist };
}

// Lists

export const artistsByName = () => [...artists()].sort((a, b) => collator.compare(a.name, b.name));

// Artist pages list albums apart from singles and EPs.
export const isAlbum = (group: ReleaseGroup) => group.type === "Album";

export const groupsByArtist = (include: (group: ReleaseGroup) => boolean) =>
  groups()
    .filter(include)
    .sort(
    (a, b) =>
      collator.compare(artistById(a.artistId)?.name ?? "", artistById(b.artistId)?.name ?? "") ||
      (groupYear(a.id) ?? 0) - (groupYear(b.id) ?? 0),
  );

export const recentlyPlayed = () =>
  groups()
    .filter((group) => group.lastPlayedAt !== null)
    .sort((a, b) => b.lastPlayedAt! - a.lastPlayedAt!);

export const recentlyAdded = () => [...groups()].sort((a, b) => b.addedAt - a.addedAt);

export const notPlayedYet = () => groups().filter((group) => group.lastPlayedAt === null);

// Finds a track by title, for seeding playlists.
export const trackIdByTitle = (title: string) =>
  Object.values(tracks()).find((track) => track.title === title)?.id;

// Changes

export function markPlayed(track: Track) {
  const release = releaseById(track.releaseId);
  if (release !== undefined) {
    setGroups((all) => all.map((group) => (group.id === release.groupId ? { ...group, lastPlayedAt: Date.now() } : group)));
  }
}

export function updateRelease(id: string, changes: Pick<Release, "title" | "year" | "format" | "label">) {
  setReleases((all) => all.map((release) => (release.id === id ? { ...release, ...changes } : release)));
}

// Removes a release and its tracks, then any release group or artist left without releases.
export function removeRelease(id: string) {
  const release = releaseById(id);
  if (release === undefined) {
    return;
  }
  setTracks((all) => Object.fromEntries(Object.entries(all).filter(([, track]) => track.releaseId !== id)));
  setReleases((all) => all.filter((other) => other.id !== id));
  if (releasesOf(release.groupId).length === 0) {
    const group = groupById(release.groupId)!;
    setGroups((all) => all.filter((other) => other.id !== group.id));
    if (groupsOf(group.artistId).length === 0) {
      setArtists((all) => all.filter((artist) => artist.id !== group.artistId));
    }
  }
}

// Each folder of files becomes one release, filed under an existing artist and release group when the names match.
export function importFiles(files: PickedFile[]) {
  const byFolder = new Map<string, PickedFile[]>();
  for (const picked of files) {
    const folder = picked.path.split("/").slice(0, -1).join("/");
    byFolder.set(folder, [...(byFolder.get(folder) ?? []), picked]);
  }
  for (const group of byFolder.values()) {
    importRelease(group);
  }
}

// Guesses tags from an "Artist/Album/01 Track.ext" layout until real metadata parsing is wired in.
function importRelease(files: PickedFile[]) {
  const sorted = [...files].sort((a, b) => collator.compare(a.path, b.path));
  const parts = sorted[0].path.split("/");
  const artistName = parts.length >= 3 ? parts[parts.length - 3] : "Unknown artist";
  const title = parts.length >= 2 ? parts[parts.length - 2] : "Unknown album";

  const artist = artists().find((existing) => collator.compare(existing.name, artistName) === 0) ?? {
    id: crypto.randomUUID(),
    name: artistName,
    country: null,
  };
  const group = groups().find(
    (existing) => existing.artistId === artist.id && collator.compare(existing.title, title) === 0,
  ) ?? {
    id: crypto.randomUUID(),
    artistId: artist.id,
    title,
    type: "Album" as const,
    addedAt: Date.now(),
    lastPlayedAt: null,
  };
  const release: Release = {
    id: crypto.randomUUID(),
    groupId: group.id,
    title,
    year: null,
    format: "Digital",
    label: null,
    country: null,
  };
  const newTracks = sorted.map((picked, n) => ({
    id: crypto.randomUUID(),
    releaseId: release.id,
    number: n + 1,
    title: picked.file.name.replace(/\.[^.]+$/, "").replace(/^\d{1,3}[\s.\-_]+/, ""),
    // Real durations arrive with metadata parsing.
    duration: 180,
  }));

  if (!artists().includes(artist)) {
    setArtists((all) => [...all, artist]);
  }
  if (!groups().includes(group)) {
    setGroups((all) => [...all, group]);
  }
  setReleases((all) => [...all, release]);
  setTracks((all) => ({ ...all, ...Object.fromEntries(newTracks.map((track) => [track.id, track])) }));
}
