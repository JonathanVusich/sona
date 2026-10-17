// Response bodies, as the server's controller.response records define them.

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
  INVALID_USERNAME: 204,
  ALREADY_REGISTERED: 205,

  RELEASE_NOT_FOUND: 300,

  INVALID_PARAMETER: 400,
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

/** The body of every client error Sona sends. */
export interface ErrorResponse {
  code: ErrorCode;
  /** What went wrong, for people. */
  reason: string;
}
