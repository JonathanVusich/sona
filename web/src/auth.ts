import { createSignal } from "solid-js";
import * as api from "./api/client";
import { ResultKind, type ApiResult } from "./api/client";
import type { ChangePasswordRequest, LoginRequest } from "./api/requests";
import { ErrorCode, Permission, type CurrentUserResponse, type TokenResponse } from "./api/responses";

// The access token is kept in memory only. The refresh token is in an HttpOnly cookie that scripts can't read, so a
// reload restores the session by refreshing it.

interface Session {
  accessToken: string;
  user: CurrentUserResponse;
}

export const PERMISSION_LABELS: Record<Permission, string> = {
  [Permission.LIBRARY_READ]: "read",
  [Permission.LIBRARY_WRITE]: "write",
  [Permission.LIBRARY_UPLOAD]: "upload",
  [Permission.USERS_MANAGE]: "manage users",
};

// What tabs tell each other over the session channel.
const SessionEvent = {
  CHANGED: "changed",
} as const;

const SESSION_CHANNEL = "sona-session";
const REFRESH_LOCK = "sona-refresh";

const SUCCEEDED: ApiResult<void> = { kind: ResultKind.OK, body: undefined };
const NOT_SIGNED_IN: ApiResult<never> = {
  kind: ResultKind.REFUSED,
  code: ErrorCode.NOT_SIGNED_IN,
  reason: "Sign in to continue.",
};

// Null while nobody is signed in.
const [session, setSession] = createSignal<Session | null>(null);
// Refreshes the tokens before they expire, while someone is signed in.
let refreshTimer: ReturnType<typeof setTimeout> | null = null;
// The refresh in progress, which every caller that asks while it runs waits for.
let pendingRefresh: Promise<boolean> | null = null;

/**
 * @returns the signed-in user, or null when nobody is signed in
 */
export function currentUser(): CurrentUserResponse | null {
  return session()?.user ?? null;
}

// Tells the other tabs that the session changed, so they restore it from the cookie they share.
const otherTabs = new BroadcastChannel(SESSION_CHANNEL);
otherTabs.onmessage = restoreSession;

/**
 * Restores the session from an earlier visit, if it is still going.
 */
export async function restoreSession(): Promise<void> {
  await refreshSession();
}

export async function logIn(request: LoginRequest): Promise<ApiResult<void>> {
  const tokens = await api.login(request);
  if (tokens.kind !== ResultKind.OK) {
    return tokens;
  }
  const started = await startSession(tokens.body);
  otherTabs.postMessage(SessionEvent.CHANGED);
  return started;
}

export async function logOut(): Promise<void> {
  // If the session has already ended, or Sona can't be reached, this tab forgets it all the same.
  await logOutLocked();
  endSession();
  otherTabs.postMessage(SessionEvent.CHANGED);
}

/**
 * Changes the password, which ends every session the user has, then logs in again with the new one.
 */
export async function changePassword(request: ChangePasswordRequest): Promise<ApiResult<void>> {
  const current = session();
  if (current === null) {
    return NOT_SIGNED_IN;
  }
  const changed = await sendAsCurrentUser((accessToken) => api.changePassword(accessToken, request));
  if (changed.kind !== ResultKind.OK) {
    return changed;
  }
  return logIn({ username: current.user.username, password: request.newPassword });
}

/**
 * @returns the headers of a STOMP CONNECT frame sent as the current user, which Sona authenticates the connection by;
 * none when nobody is signed in, so Sona refuses it
 */
export function connectHeadersAsCurrentUser(): Record<string, string> {
  const current = session();
  if (current === null) {
    return {};
  }
  return { Authorization: `Bearer ${current.accessToken}` };
}

export function hasPermission(permission: Permission): boolean {
  return currentUser()?.permissions.includes(permission) ?? false;
}

/**
 * Calls Sona as the current user. An expired access token is refreshed once and the call made again.
 */
export async function sendAsCurrentUser<T>(
  call: (accessToken: string) => Promise<ApiResult<T>>,
): Promise<ApiResult<T>> {
  const result = await sendWithSession(call);
  if (!isRefused(result, ErrorCode.NOT_SIGNED_IN)) {
    return result;
  }
  const refreshed = await refreshSession();
  if (!refreshed) {
    return result;
  }
  return sendWithSession(call);
}

async function sendWithSession<T>(call: (accessToken: string) => Promise<ApiResult<T>>): Promise<ApiResult<T>> {
  const current = session();
  if (current === null) {
    return NOT_SIGNED_IN;
  }
  return call(current.accessToken);
}

function isRefused(result: ApiResult<unknown>, code: ErrorCode): boolean {
  return result.kind === ResultKind.REFUSED && result.code === code;
}

/**
 * Swaps the refresh cookie for new tokens, once at a time however many callers ask.
 *
 * @returns whether the session is still going
 */
async function refreshSession(): Promise<boolean> {
  pendingRefresh ??= refreshSessionLocked();
  const refreshed = await pendingRefresh;
  pendingRefresh = null;
  return refreshed;
}

/**
 * Sona ends the whole session when a refresh token is used twice, so tabs take turns: each sends the cookie the tab
 * before it was given.
 */
async function refreshSessionLocked(): Promise<boolean> {
  // Browsers only keep the Secure refresh cookie, and only offer Web Locks, over HTTPS or on localhost.
  if (!window.isSecureContext) {
    return false;
  }
  return await navigator.locks.request(REFRESH_LOCK, async () => {
    const tokens = await api.refreshTokens();
    if (isRefused(tokens, ErrorCode.INVALID_REFRESH_TOKEN)) {
      endSession();
    }
    if (tokens.kind !== ResultKind.OK) {
      return false;
    }
    const started = await startSession(tokens.body);
    return started.kind === ResultKind.OK;
  });
}

/**
 * Ends the session at Sona once no tab is refreshing it, so it sends the newest refresh cookie rather than one another
 * tab is replacing.
 */
async function logOutLocked(): Promise<ApiResult<void>> {
  // Without a secure context there is no refresh cookie to end the session with.
  if (!window.isSecureContext) {
    return NOT_SIGNED_IN;
  }
  return await navigator.locks.request(REFRESH_LOCK, () => api.logout());
}

async function startSession(tokens: TokenResponse): Promise<ApiResult<void>> {
  const user = await api.fetchCurrentUser(tokens.accessToken);
  if (user.kind !== ResultKind.OK) {
    return user;
  }
  setSession({ accessToken: tokens.accessToken, user: user.body });
  stopRefreshTimer();
  // A minute early, or halfway through for tokens that last less than two minutes.
  const refreshIn = Math.max(tokens.expiresIn - 60, tokens.expiresIn / 2);
  refreshTimer = setTimeout(refreshSession, refreshIn * 1000);
  return SUCCEEDED;
}

function endSession() {
  stopRefreshTimer();
  setSession(null);
}

function stopRefreshTimer() {
  if (refreshTimer !== null) {
    clearTimeout(refreshTimer);
    refreshTimer = null;
  }
}
