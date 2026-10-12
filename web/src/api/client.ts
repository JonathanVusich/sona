import type { ChangePasswordRequest, LoginRequest } from "./requests";
import type { CurrentUserResponse, ErrorCode, ErrorResponse, TokenResponse } from "./responses";
import { ApiRoutes } from "./routes";

// Calls to Sona's HTTP API, one function per endpoint. The frontend is served from Sona's origin, so the refresh cookie
// travels with the auth calls. Calls never throw: a failure is returned, with Sona's ErrorResponse when it refused the
// request.

/** Why a call failed. The code is Sona's, or null when Sona couldn't answer. */
export interface ApiFailure {
  code: ErrorCode | null;
  reason: string;
}

export type ApiResult<T> = { ok: true; body: T } | { ok: false; failure: ApiFailure };

const HttpMethod = {
  GET: "GET",
  POST: "POST",
} as const;

type HttpMethod = (typeof HttpMethod)[keyof typeof HttpMethod];

interface RequestOptions {
  accessToken?: string;
  body?: LoginRequest | ChangePasswordRequest;
}

export async function login(request: LoginRequest): Promise<ApiResult<TokenResponse>> {
  return sendForJson(HttpMethod.POST, ApiRoutes.AUTH_TOKEN, { body: request });
}

export async function refreshTokens(): Promise<ApiResult<TokenResponse>> {
  return sendForJson(HttpMethod.POST, ApiRoutes.AUTH_REFRESH);
}

export async function logout(): Promise<ApiResult<void>> {
  return sendForNothing(HttpMethod.POST, ApiRoutes.AUTH_LOGOUT);
}

export async function fetchCurrentUser(accessToken: string): Promise<ApiResult<CurrentUserResponse>> {
  return sendForJson(HttpMethod.GET, ApiRoutes.CURRENT_USER, { accessToken });
}

export async function changePassword(accessToken: string, request: ChangePasswordRequest): Promise<ApiResult<void>> {
  return sendForNothing(HttpMethod.POST, ApiRoutes.CURRENT_USER_PASSWORD, { accessToken, body: request });
}

async function sendForJson<T>(method: HttpMethod, path: string, options?: RequestOptions): Promise<ApiResult<T>> {
  const result = await sendRequest(method, path, options);
  return result.ok ? { ok: true, body: await result.body.json() } : result;
}

async function sendForNothing(method: HttpMethod, path: string, options?: RequestOptions): Promise<ApiResult<void>> {
  const result = await sendRequest(method, path, options);
  return result.ok ? { ok: true, body: undefined } : result;
}

async function sendRequest(method: HttpMethod, path: string, options: RequestOptions = {}): Promise<ApiResult<Response>> {
  const { accessToken, body } = options;
  let response: Response;
  // The one place the frontend catches: fetch rejects when Sona can't be reached at all.
  try {
    response = await fetch(path, {
      method,
      headers: {
        ...(accessToken === undefined ? {} : { Authorization: `Bearer ${accessToken}` }),
        ...(body === undefined ? {} : { "Content-Type": "application/json" }),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    return { ok: false, failure: { code: null, reason: "Can't reach Sona. Check your connection and try again." } };
  }
  if (response.ok) {
    return { ok: true, body: response };
  }
  if (response.status < 500) {
    // Every client error Sona sends has an ErrorResponse body.
    const error: ErrorResponse = await response.json();
    return { ok: false, failure: error };
  }
  return { ok: false, failure: { code: null, reason: "Something went wrong. Try again." } };
}
