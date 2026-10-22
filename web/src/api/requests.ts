// Request bodies, as the server's controller.request records define them.

export interface LoginRequest {
  username: string;
  password: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}
/** Where in a list Sona serves a page at a time a page starts. */
export const PagePosition = {
  FIRST: "first",
  AFTER: "after",
} as const;

export type PagePosition = (typeof PagePosition)[keyof typeof PagePosition];

/** Which page of a list to load: the first, or the one after a given item. */
export type PageRequest =
  { position: typeof PagePosition.FIRST } | { position: typeof PagePosition.AFTER; after: string };

/** What an ingest group's files have in common, as IngestGroupKind names it on the server. */
export const IngestGroupKind = {
  // Files that belong together, such as one folder.
  ALBUM: "ALBUM",
  // Loose files, which matching groups itself.
  UNKNOWN: "UNKNOWN",
} as const;

export type IngestGroupKind = (typeof IngestGroupKind)[keyof typeof IngestGroupKind];

export interface IngestGroupRequest {
  kind: IngestGroupKind;
  name: string;
}
