import type { ChangePasswordRequest, LoginRequest } from "./requests";
import type { CurrentUserResponse, ErrorCode, ErrorResponse, TokenResponse } from "./responses";
import { ApiRoutes } from "./routes";

// Calls to Sona's HTTP API, one function per endpoint. The frontend is served from Sona's origin, so the refresh cookie
// travels with the auth calls.

/** Sona refused the request, as the server's ClientException does. */
export class ClientError extends Error {
  readonly code: ErrorCode;

  constructor(
    readonly status: number,
    response: ErrorResponse,
  ) {
    super(response.reason);
    this.code = response.code;
  }
}

interface RequestOptions {
  accessToken?: string;
  body?: LoginRequest | ChangePasswordRequest;
}

export async function login(request: LoginRequest): Promise<TokenResponse> {
  const response = await sendRequest("POST", ApiRoutes.AUTH_TOKEN, { body: request });
  return response.json();
}

export async function refreshTokens(): Promise<TokenResponse> {
  const response = await sendRequest("POST", ApiRoutes.AUTH_REFRESH);
  return response.json();
}

export async function logout(): Promise<void> {
  await sendRequest("POST", ApiRoutes.AUTH_LOGOUT);
}

export async function fetchCurrentUser(accessToken: string): Promise<CurrentUserResponse> {
  const response = await sendRequest("GET", ApiRoutes.CURRENT_USER, { accessToken });
  return response.json();
}

export async function changePassword(accessToken: string, request: ChangePasswordRequest): Promise<void> {
  await sendRequest("POST", ApiRoutes.CURRENT_USER_PASSWORD, { accessToken, body: request });
}

/**
 * @returns what went wrong, for people: Sona's reason when it refused the request
 */
export function describeError(error: unknown): string {
  return error instanceof ClientError ? error.message : "Something went wrong. Try again.";
}

/**
 * @throws ClientError when Sona refuses the request, whose body is then an ErrorResponse
 */
async function sendRequest(method: "GET" | "POST", path: string, options: RequestOptions = {}): Promise<Response> {
  const { accessToken, body } = options;
  const response = await fetch(path, {
    method,
    headers: {
      ...(accessToken === undefined ? {} : { Authorization: `Bearer ${accessToken}` }),
      ...(body === undefined ? {} : { "Content-Type": "application/json" }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (response.ok) {
    return response;
  }
  if (response.status < 500) {
    throw new ClientError(response.status, await response.json());
  }
  throw new Error(`Sona answered ${method} ${path} with ${response.status}.`);
}
