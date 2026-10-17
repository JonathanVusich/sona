import type { ChangePasswordRequest, LoginRequest } from "./requests";
import type { CurrentUserResponse, ErrorCode, ErrorResponse, ReleaseResponse, TokenResponse } from "./responses";
import { ApiRoutes } from "./routes";

// Calls to Sona's HTTP API, one function per endpoint. The frontend is served from Sona's origin, so the refresh cookie
// travels with the auth calls. Calls never throw: a failure is returned, with Sona's ErrorResponse when it refused the
// request.

export const FailureKind = {
  // Sona answered with an ErrorResponse, whose code clients branch on.
  REFUSED: "refused",
  // Sona couldn't be reached, or failed to answer.
  FAILED: "failed",
} as const;

export type FailureKind = (typeof FailureKind)[keyof typeof FailureKind];

/** Why a call failed. */
export type ApiFailure =
  | { kind: typeof FailureKind.REFUSED; code: ErrorCode; reason: string }
  | { kind: typeof FailureKind.FAILED; reason: string };

export type ApiResult<T> = { ok: true; body: T } | { ok: false; failure: ApiFailure };

const HttpMethod = {
  GET: "GET",
  POST: "POST",
} as const;

type HttpMethod = (typeof HttpMethod)[keyof typeof HttpMethod];

// What a request sends as JSON.
type RequestBody = LoginRequest | ChangePasswordRequest;

const UNREACHABLE: ApiFailure = {
  kind: FailureKind.FAILED,
  reason: "Can't reach Sona. Check your connection and try again.",
};
const SERVER_ERROR: ApiFailure = { kind: FailureKind.FAILED, reason: "Something went wrong. Try again." };

export async function login(request: LoginRequest): Promise<ApiResult<TokenResponse>> {
  return sendForJson(HttpMethod.POST, ApiRoutes.AUTH_TOKEN, null, request);
}

export async function refreshTokens(): Promise<ApiResult<TokenResponse>> {
  return sendForJson(HttpMethod.POST, ApiRoutes.AUTH_REFRESH, null, null);
}

export async function logout(): Promise<ApiResult<void>> {
  return sendForNothing(HttpMethod.POST, ApiRoutes.AUTH_LOGOUT, null, null);
}

export async function fetchCurrentUser(accessToken: string): Promise<ApiResult<CurrentUserResponse>> {
  return sendForJson(HttpMethod.GET, ApiRoutes.CURRENT_USER, accessToken, null);
}

export async function changePassword(accessToken: string, request: ChangePasswordRequest): Promise<ApiResult<void>> {
  return sendForNothing(HttpMethod.POST, ApiRoutes.CURRENT_USER_PASSWORD, accessToken, request);
}

export async function fetchRelease(accessToken: string, releaseId: string): Promise<ApiResult<ReleaseResponse>> {
  const path = `${ApiRoutes.RELEASES}/${encodeURIComponent(releaseId)}`;
  return sendForJson(HttpMethod.GET, path, accessToken, null);
}

async function sendForJson<T>(
  method: HttpMethod,
  path: string,
  accessToken: string | null,
  body: RequestBody | null,
): Promise<ApiResult<T>> {
  const result = await sendRequest(method, path, accessToken, body);
  if (!result.ok) {
    return result;
  }
  return { ok: true, body: await result.body.json() };
}

async function sendForNothing(
  method: HttpMethod,
  path: string,
  accessToken: string | null,
  body: RequestBody | null,
): Promise<ApiResult<void>> {
  const result = await sendRequest(method, path, accessToken, body);
  if (!result.ok) {
    return result;
  }
  return { ok: true, body: undefined };
}

async function sendRequest(
  method: HttpMethod,
  path: string,
  accessToken: string | null,
  body: RequestBody | null,
): Promise<ApiResult<Response>> {
  let response: Response;
  // The one place the frontend catches: fetch rejects when Sona can't be reached at all.
  try {
    response = await fetch(path, {
      method,
      headers: { ...authorizationHeader(accessToken), ...contentTypeHeader(body) },
      body: requestBodyText(body),
    });
  } catch {
    return { ok: false, failure: UNREACHABLE };
  }
  if (response.ok) {
    return { ok: true, body: response };
  }
  if (response.status < 500) {
    // Every client error Sona sends has an ErrorResponse body.
    const error: ErrorResponse = await response.json();
    return { ok: false, failure: { kind: FailureKind.REFUSED, code: error.code, reason: error.reason } };
  }
  return { ok: false, failure: SERVER_ERROR };
}

/**
 * @param accessToken null for the auth calls, which the refresh cookie authenticates if anything
 */
function authorizationHeader(accessToken: string | null): Record<string, string> {
  if (accessToken === null) {
    return {};
  }
  return { Authorization: `Bearer ${accessToken}` };
}

function contentTypeHeader(body: RequestBody | null): Record<string, string> {
  if (body === null) {
    return {};
  }
  return { "Content-Type": "application/json" };
}

function requestBodyText(body: RequestBody | null): string | null {
  if (body === null) {
    return null;
  }
  return JSON.stringify(body);
}
