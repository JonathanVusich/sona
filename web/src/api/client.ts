import axios, { type AxiosProgressEvent, type AxiosRequestConfig, type AxiosResponse } from "axios";
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
  StreamTokenResponse,
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

// Answers every status rather than rejecting on errors, so only a request that never got an answer rejects.
const http = axios.create({ validateStatus: () => true });

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

export async function fetchStreamToken(accessToken: string, trackId: string): Promise<ApiResult<StreamTokenResponse>> {
  const path = `${trackPath(trackId)}${ApiRoutes.TRACK_STREAM_TOKEN}`;
  return sendForJson(path, getAsUser(accessToken));
}

/**
 * @returns where an audio element streams the track from, which Sona answers in the parts it asks for as it seeks
 */
export function trackStreamUrl(trackId: string, streamToken: string): string {
  const query = new URLSearchParams({ access_token: streamToken });
  return `${trackPath(trackId)}${ApiRoutes.TRACK_STREAM}?${query}`;
}

function trackPath(trackId: string): string {
  return `${ApiRoutes.TRACKS}/${encodeURIComponent(trackId)}`;
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
 *
 * @param onProgress called as the file is sent, with how much of it has been, from 0 to 1
 */
export async function uploadTrack(
  accessToken: string,
  groupIngestId: string,
  file: File,
  onProgress: (sent: number) => void,
): Promise<ApiResult<TrackIngestResponse>> {
  const query = new URLSearchParams({ filename: file.name });
  const path = `${ApiRoutes.INGEST_GROUPS}/${encodeURIComponent(groupIngestId)}/tracks?${query}`;
  return sendForJson(path, postFileAsUser(accessToken, file, onProgress));
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

function post(): AxiosRequestConfig {
  return { method: HttpMethod.POST };
}

function postJson(body: RequestBody): AxiosRequestConfig {
  return { method: HttpMethod.POST, headers: JSON_CONTENT, data: body };
}

function getAsUser(accessToken: string): AxiosRequestConfig {
  return { method: HttpMethod.GET, headers: authorizationHeader(accessToken) };
}

function postJsonAsUser(accessToken: string, body: RequestBody): AxiosRequestConfig {
  return { method: HttpMethod.POST, headers: { ...authorizationHeader(accessToken), ...JSON_CONTENT }, data: body };
}

function postFileAsUser(accessToken: string, file: File, onProgress: (sent: number) => void): AxiosRequestConfig {
  return {
    method: HttpMethod.POST,
    headers: { ...authorizationHeader(accessToken), ...FILE_CONTENT },
    data: file,
    onUploadProgress: (event) => onProgress(sentFraction(file, event)),
  };
}

// The event's own total is missing when the browser can't tell it, but the file's size is always known.
function sentFraction(file: File, event: AxiosProgressEvent): number {
  if (file.size === 0) {
    return 1;
  }
  return Math.min(event.loaded / file.size, 1);
}

function authorizationHeader(accessToken: string): Record<string, string> {
  return { Authorization: `Bearer ${accessToken}` };
}

async function sendForJson<T>(path: string, request: AxiosRequestConfig): Promise<ApiResult<T>> {
  const result = await sendRequest(path, request);
  if (result.kind !== ResultKind.OK) {
    return result;
  }
  return { kind: ResultKind.OK, body: result.body.data };
}

async function sendForNothing(path: string, request: AxiosRequestConfig): Promise<ApiResult<void>> {
  const result = await sendRequest(path, request);
  if (result.kind !== ResultKind.OK) {
    return result;
  }
  return { kind: ResultKind.OK, body: undefined };
}

async function sendRequest(path: string, request: AxiosRequestConfig): Promise<ApiResult<AxiosResponse>> {
  let response: AxiosResponse;
  // The one place the frontend catches: axios rejects when Sona can't be reached at all, as every status is answered
  // below.
  try {
    response = await http.request({ ...request, url: path });
  } catch {
    return UNREACHABLE;
  }
  if (response.status < 300) {
    return { kind: ResultKind.OK, body: response };
  }
  if (response.status < 500) {
    // Every client error Sona sends has an ErrorResponse body.
    const error: ErrorResponse = response.data;
    return { kind: ResultKind.REFUSED, code: error.code, reason: error.reason };
  }
  return SERVER_ERROR;
}
