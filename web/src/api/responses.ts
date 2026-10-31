// Response bodies, as the server's controller.response records define them.

import type { IngestGroupKind } from "./requests";

export const UserRole = {
  ADMIN: "ADMIN",
  USER: "USER",
} as const;

export type UserRole = (typeof UserRole)[keyof typeof UserRole];

export const Permission = {
  LIBRARY_READ: "LIBRARY_READ",
  LIBRARY_WRITE: "LIBRARY_WRITE",
  LIBRARY_UPLOAD: "LIBRARY_UPLOAD",
  USERS_MANAGE: "USERS_MANAGE",
} as const;

export type Permission = (typeof Permission)[keyof typeof Permission];

export const SessionState = {
  ACTIVE: "ACTIVE",
  // Until they change their password, the user may only do that.
  PASSWORD_CHANGE_REQUIRED: "PASSWORD_CHANGE_REQUIRED",
} as const;

export type SessionState = (typeof SessionState)[keyof typeof SessionState];

export const TokenType = {
  BEARER: "BEARER",
} as const;

export type TokenType = (typeof TokenType)[keyof typeof TokenType];

/** Lets an audio element stream the one track it was issued for, as the access_token parameter of its stream URL. */
export interface StreamTokenResponse {
  token: string;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: TokenType;
  /** Seconds until the access token expires. */
  expiresIn: number;
}

export interface CurrentUserResponse {
  userId: string;
  username: string;
  role: UserRole;
  state: SessionState;
  /** What the user may do now, which is nothing while they must change their password. */
  permissions: Permission[];
  /** The largest file Sona accepts as an upload, in bytes. */
  maxUploadSize: number;
  /** The extensions of the files Sona can ingest, lowercase and without the dot. */
  uploadExtensions: string[];
}

/** Why Sona refused a request. Clients branch on the number, which never changes once released. */
export const ErrorCode = {
  NOT_SIGNED_IN: 100,
  INVALID_CREDENTIALS: 101,
  INVALID_REFRESH_TOKEN: 102,
  PASSWORD_CHANGE_REQUIRED: 103,
  ACCESS_DENIED: 104,
  ACCOUNT_DISABLED: 105,
  SIGN_IN_FAILED: 106,
  INVALID_REGISTRATION_TOKEN: 107,
  SIGN_IN_REQUIRED: 108,

  INCORRECT_CURRENT_PASSWORD: 200,
  INVALID_NEW_PASSWORD: 201,
  PASSWORD_MANAGED_BY_PROVIDER: 202,
  USERNAME_TAKEN: 203,
  ALREADY_REGISTERED: 205,

  RELEASE_NOT_FOUND: 300,
  RELEASE_GROUP_NOT_FOUND: 301,
  ARTIST_NOT_FOUND: 302,
  UNSUPPORTED_FORMAT: 303,
  INGEST_GROUP_NOT_FOUND: 304,
  FILE_TOO_LARGE: 305,
  TRACK_NOT_FOUND: 306,
  PLAYLIST_NOT_FOUND: 307,
  INGEST_TRACK_NOT_FOUND: 309,
  INGEST_ALREADY_UPLOADED: 310,

  INVALID_PARAMETER: 400,
  UNSUPPORTED_CONTENT_TYPE: 401,
} as const;

export type ErrorCode = (typeof ErrorCode)[keyof typeof ErrorCode];

export interface ArtistResponse {
  artistId: string;
  name: string;
}

export interface LabelResponse {
  labelId: string;
  name: string;
}

export interface TrackResponse {
  trackId: string;
  name: string;
  /** In seconds. */
  duration: number;
  /** On its medium, from 1. */
  position: number;
  /** The track's own artist, who may not be the release's (as on a compilation). */
  artist: ArtistResponse;
}

export interface MediumResponse {
  /** On its release, from 1. */
  position: number;
  /** Like "CD" or "Digital Media"; null when MusicBrainz doesn't know. */
  format: string | null;
  tracks: TrackResponse[];
}

/** Everything the release page shows. */
export interface ReleaseResponse {
  releaseId: string;
  name: string;
  /** An ISO date, null when unknown. MusicBrainz may only know the year or month: "2002" is 2002-01-01. */
  date: string | null;
  /** ISO 3166-1 code, or a MusicBrainz code like "XW" for worldwide; null when unknown. */
  country: string | null;
  /** How many of the release's tracks are in the library. */
  trackCount: number;
  releaseGroupId: string;
  artist: ArtistResponse;
  labels: LabelResponse[];
  /**
   * Each medium's format in order, whether or not any of its tracks is in the library; null where MusicBrainz doesn't
   * know it.
   */
  formats: (string | null)[];
  /** Those with a track in the library, each with those tracks. */
  media: MediumResponse[];
}

/** MusicBrainz's primary release group types. */
export const ReleaseGroupType = {
  ALBUM: "ALBUM",
  SINGLE: "SINGLE",
  EP: "EP",
  BROADCAST: "BROADCAST",
  OTHER: "OTHER",
} as const;

export type ReleaseGroupType = (typeof ReleaseGroupType)[keyof typeof ReleaseGroupType];

/** A release group as lists of them show it. */
export interface ReleaseGroupSummaryResponse {
  releaseGroupId: string;
  name: string;
  /** Null when MusicBrainz gives the release group no primary type. */
  type: ReleaseGroupType | null;
  /** The group's earliest release anywhere, as an ISO date; null when unknown. */
  firstReleaseDate: string | null;
  artist: ArtistResponse;
}

/** One page of the release groups, by name. */
export interface ReleaseGroupPageResponse {
  releaseGroups: ReleaseGroupSummaryResponse[];
  /** The ID to load the next page after, or null on the last page. */
  next: string | null;
}

/** One of a release group's releases, as its page lists them. */
export interface EditionResponse {
  releaseId: string;
  name: string;
  /** An ISO date, null when unknown, as on ReleaseResponse. */
  date: string | null;
  /** Null when unknown, as on ReleaseResponse. */
  country: string | null;
  /** How many of the release's tracks are in the library. */
  trackCount: number;
  labels: LabelResponse[];
  /** Each medium's format in order, null where MusicBrainz doesn't know it. */
  formats: (string | null)[];
}

/** The release that stands for its group, whose tracks the release group page shows. */
export interface DefaultReleaseResponse {
  releaseId: string;
  media: MediumResponse[];
}

/** Everything the release group page shows. */
export interface ReleaseGroupResponse {
  releaseGroupId: string;
  name: string;
  /** Null when MusicBrainz gives the release group no primary type. */
  type: ReleaseGroupType | null;
  /** The group's earliest release anywhere, as an ISO date; null when unknown. */
  firstReleaseDate: string | null;
  artist: ArtistResponse;
  /** The group's earliest release, as editions lists it first. */
  defaultRelease: DefaultReleaseResponse;
  /** Earliest first, and those with no known date last. */
  editions: EditionResponse[];
}

/** An artist as the list of artists shows them. */
export interface ArtistSummaryResponse {
  artistId: string;
  name: string;
  /** How many of the artist's release groups are in the library. */
  releaseGroupCount: number;
}

/** One page of the artists with a release group in the library, by name. */
export interface ArtistPageResponse {
  artists: ArtistSummaryResponse[];
  /** The ID to load the next page after, or null on the last page. */
  next: string | null;
}

/** One of an artist's release groups, as the artist page lists them. */
export interface ArtistReleaseGroupResponse {
  releaseGroupId: string;
  name: string;
  /** Null when MusicBrainz gives the release group no primary type. */
  type: ReleaseGroupType | null;
  /** The group's earliest release anywhere, as an ISO date; null when unknown. */
  firstReleaseDate: string | null;
}

/** Everything the artist page shows. */
export interface ArtistDetailsResponse {
  artistId: string;
  name: string;
  /** ISO 3166-1 code of the artist's area; null when MusicBrainz doesn't know it. */
  country: string | null;
  /** Newest first, and those with no known date last. */
  releaseGroups: ArtistReleaseGroupResponse[];
}

/** The body of every client error Sona sends. */
export interface ErrorResponse {
  code: ErrorCode;
  /** What went wrong, for people. */
  reason: string;
}

/** How far a file's ingest has got. */
export const IngestState = {
  // Its ingest is made, and its bytes are yet to be sent.
  CREATED: "CREATED",
  UPLOADING: "UPLOADING",
  // Its bytes are stored, and it waits to be matched.
  UPLOADED: "UPLOADED",
  INPUT_REQUIRED: "INPUT_REQUIRED",
  MOVE_FAILED: "MOVE_FAILED",
  FAILED: "FAILED",
  COMPLETED: "COMPLETED",
} as const;

export type IngestState = (typeof IngestState)[keyof typeof IngestState];

/** A group of files to upload, under the ID Sona gave it. */
export interface IngestGroupResponse {
  groupIngestId: string;
  kind: IngestGroupKind;
  name: string;
  /** The group's files, in the order they were listed, each under the ID it is uploaded to. */
  tracks: TrackIngestResponse[];
}

/** One file of an upload, and how far its ingest has got. */
export interface TrackIngestResponse {
  trackIngestId: string;
  groupIngestId: string;
  /** The file's name where it was uploaded from. */
  originalFileName: string;
  state: IngestState;
}

/** The release a track is on, as lists of tracks from many releases name and link it. */
export interface TrackReleaseResponse {
  releaseId: string;
  name: string;
}

/** A playlist as the list of the user's playlists shows it. */
export interface PlaylistSummaryResponse {
  playlistId: string;
  name: string;
  trackCount: number;
  /** The names of the release groups its first tracks are from, up to four, for its cover. */
  coverNames: string[];
}

export interface PlaylistsResponse {
  /** Every playlist the user has, by name. */
  playlists: PlaylistSummaryResponse[];
}

/** A track on a playlist, with the release it's on. Its position is on its medium, not on the playlist. */
export interface PlaylistTrackResponse extends TrackResponse {
  release: TrackReleaseResponse;
}

/** Everything the playlist page shows. */
export interface PlaylistResponse {
  playlistId: string;
  name: string;
  /** Null when the user gave it none. */
  description: string | null;
  /** Every track on the playlist, in the user's order. */
  tracks: PlaylistTrackResponse[];
}

/** A track in the play history, with the release it's on. */
export interface PlayedTrackResponse extends TrackResponse {
  release: TrackReleaseResponse;
}

/** One time the user played a track. */
export interface PlayResponse {
  playId: string;
  track: PlayedTrackResponse;
}

/** One page of the user's plays, latest first, a track played again listed again. */
export interface PlayPageResponse {
  plays: PlayResponse[];
  /** What to load the next page after, or null on the last page. */
  next: string | null;
}

export interface CreatedPlaylistResponse {
  playlistId: string;
}

export interface AddedTracksResponse {
  /** How many of the tracks were added; those already on the playlist aren't. */
  added: number;
}
