import {
  PagePosition,
  type ChangePasswordRequest,
  type IngestGroupRequest,
  type LoginRequest,
  type PageRequest,
} from "./requests";
import type {
  ArtistDetailsResponse,
  ArtistPageResponse,
  CurrentUserResponse,
  ErrorCode,
  ErrorResponse,
  IngestGroupResponse,
  ReleaseGroupPageResponse,
  ReleaseGroupResponse,
  ReleaseResponse,
  TokenResponse,
  TrackIngestResponse,
} from "./responses";
import { ApiRoutes } from "./routes";

// Calls to Sona's HTTP API, one function per endpoint. The frontend is served from Sona's origin, so the refresh cookie
// travels with the auth calls. Calls never throw: a failure is returned, with Sona's ErrorResponse when it refused the
// request.

export const ResultKind = {
  // Sona answered with the body asked for.
  OK: "ok",
  // Sona answered with an ErrorResponse, whose code clients branch on.
  REFUSED: "refused",
  // Sona couldn't be reached, or failed to answer.
  FAILED: "failed",
} as const;

export type ResultKind = (typeof ResultKind)[keyof typeof ResultKind];

export type ApiResult<T> =
  | { kind: typeof ResultKind.OK; body: T }
  | { kind: typeof ResultKind.REFUSED; code: ErrorCode; reason: string }
  | { kind: typeof ResultKind.FAILED; reason: string };

const HttpMethod = {
  GET: "GET",
  POST: "POST",
} as const;

type HttpMethod = (typeof HttpMethod)[keyof typeof HttpMethod];

// What a request sends as JSON.
type RequestBody = LoginRequest | ChangePasswordRequest | IngestGroupRequest;

const JSON_CONTENT = { "Content-Type": "application/json" };
// Sona reads an uploaded file as the raw request body; a form type would have it read the file as form fields.
const FILE_CONTENT = { "Content-Type": "application/octet-stream" };

const UNREACHABLE: ApiResult<never> = {
  kind: ResultKind.FAILED,
  reason: "Can't reach Sona. Check your connection and try again.",
};
const SERVER_ERROR: ApiResult<never> = { kind: ResultKind.FAILED, reason: "Something went wrong. Try again." };

export async function login(request: LoginRequest): Promise<ApiResult<TokenResponse>> {
  return sendForJson(ApiRoutes.AUTH_TOKEN, postJson(request));
}

export async function refreshTokens(): Promise<ApiResult<TokenResponse>> {
  return sendForJson(ApiRoutes.AUTH_REFRESH, post());
}

export async function logout(): Promise<ApiResult<void>> {
  return sendForNothing(ApiRoutes.AUTH_LOGOUT, post());
}

export async function fetchCurrentUser(accessToken: string): Promise<ApiResult<CurrentUserResponse>> {
  return sendForJson(ApiRoutes.CURRENT_USER, getAsUser(accessToken));
}

export async function changePassword(accessToken: string, request: ChangePasswordRequest): Promise<ApiResult<void>> {
  return sendForNothing(ApiRoutes.CURRENT_USER_PASSWORD, postJsonAsUser(accessToken, request));
}

export async function fetchRelease(accessToken: string, releaseId: string): Promise<ApiResult<ReleaseResponse>> {
  const path = `${ApiRoutes.RELEASES}/${encodeURIComponent(releaseId)}`;
  return sendForJson(path, getAsUser(accessToken));
}

export async function fetchReleaseGroups(
  accessToken: string,
  page: PageRequest,
): Promise<ApiResult<ReleaseGroupPageResponse>> {
  return sendForJson(`${ApiRoutes.RELEASE_GROUPS}${pageQuery(page)}`, getAsUser(accessToken));
}

export async function fetchReleaseGroup(
  accessToken: string,
  releaseGroupId: string,
): Promise<ApiResult<ReleaseGroupResponse>> {
  const path = `${ApiRoutes.RELEASE_GROUPS}/${encodeURIComponent(releaseGroupId)}`;
  return sendForJson(path, getAsUser(accessToken));
}

export async function fetchArtists(accessToken: string, page: PageRequest): Promise<ApiResult<ArtistPageResponse>> {
  return sendForJson(`${ApiRoutes.ARTISTS}${pageQuery(page)}`, getAsUser(accessToken));
}

export async function fetchArtist(accessToken: string, artistId: string): Promise<ApiResult<ArtistDetailsResponse>> {
  const path = `${ApiRoutes.ARTISTS}/${encodeURIComponent(artistId)}`;
  return sendForJson(path, getAsUser(accessToken));
}

/**
 * Creates an ingest group, under an ID Sona gives it.
 */
export async function createIngestGroup(
  accessToken: string,
  request: IngestGroupRequest,
): Promise<ApiResult<IngestGroupResponse>> {
  return sendForJson(ApiRoutes.INGEST_GROUPS, postJsonAsUser(accessToken, request));
}

/**
 * Uploads one file into an ingest group, under the ID Sona gave the group.
 */
export async function uploadTrack(
  accessToken: string,
  groupIngestId: string,
  file: File,
): Promise<ApiResult<TrackIngestResponse>> {
  const query = new URLSearchParams({ filename: file.name });
  const path = `${ApiRoutes.INGEST_GROUPS}/${encodeURIComponent(groupIngestId)}/tracks?${query}`;
  return sendForJson(path, postFileAsUser(accessToken, file));
}

function pageQuery(page: PageRequest): string {
  switch (page.position) {
    case PagePosition.FIRST:
      return "";
    case PagePosition.AFTER:
      return `?${new URLSearchParams({ after: page.after })}`;
  }
}

// One function per kind of request. The auth calls send no access token: the refresh cookie authenticates them if
// anything.

function post(): RequestInit {
  return { method: HttpMethod.POST };
}

function postJson(body: RequestBody): RequestInit {
  return { method: HttpMethod.POST, headers: JSON_CONTENT, body: JSON.stringify(body) };
}

function getAsUser(accessToken: string): RequestInit {
  return { method: HttpMethod.GET, headers: authorizationHeader(accessToken) };
}

function postJsonAsUser(accessToken: string, body: RequestBody): RequestInit {
  return {
    method: HttpMethod.POST,
    headers: { ...authorizationHeader(accessToken), ...JSON_CONTENT },
    body: JSON.stringify(body),
  };
}

function postFileAsUser(accessToken: string, file: File): RequestInit {
  return { method: HttpMethod.POST, headers: { ...authorizationHeader(accessToken), ...FILE_CONTENT }, body: file };
}

function authorizationHeader(accessToken: string): Record<string, string> {
  return { Authorization: `Bearer ${accessToken}` };
}

async function sendForJson<T>(path: string, request: RequestInit): Promise<ApiResult<T>> {
  const result = await sendRequest(path, request);
  if (result.kind !== ResultKind.OK) {
    return result;
  }
  return { kind: ResultKind.OK, body: await result.body.json() };
}

async function sendForNothing(path: string, request: RequestInit): Promise<ApiResult<void>> {
  const result = await sendRequest(path, request);
  if (result.kind !== ResultKind.OK) {
    return result;
  }
  return { kind: ResultKind.OK, body: undefined };
}

async function sendRequest(path: string, request: RequestInit): Promise<ApiResult<Response>> {
  let response: Response;
  // The one place the frontend catches: fetch rejects when Sona can't be reached at all.
  try {
    response = await fetch(path, request);
  } catch {
    return UNREACHABLE;
  }
  if (response.ok) {
    return { kind: ResultKind.OK, body: response };
  }
  if (response.status < 500) {
    // Every client error Sona sends has an ErrorResponse body.
    const error: ErrorResponse = await response.json();
    return { kind: ResultKind.REFUSED, code: error.code, reason: error.reason };
  }
  return SERVER_ERROR;
}
