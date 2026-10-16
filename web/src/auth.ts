import { createSignal } from "solid-js";
import * as api from "./api/client";
import { FailureKind, type ApiFailure, type ApiResult } from "./api/client";
import type { ChangePasswordRequest, LoginRequest } from "./api/requests";
import { ErrorCode, Permission, type CurrentUserResponse, type TokenResponse } from "./api/responses";

// The access token is kept in memory only. The refresh token is in an HttpOnly cookie that scripts can't read, so a
// reload restores the session by refreshing it.

const SessionStatus = {
  SIGNED_OUT: "signed-out",
  SIGNED_IN: "signed-in",
} as const;

// A signed-in session refreshes its tokens on a timer before they expire.
type Session =
  | { status: typeof SessionStatus.SIGNED_OUT }
  | {
      status: typeof SessionStatus.SIGNED_IN;
      accessToken: string;
      user: CurrentUserResponse;
      refreshTimer: ReturnType<typeof setTimeout>;
    };

const RefreshStatus = {
  IDLE: "idle",
  REFRESHING: "refreshing",
} as const;

// A refresh in progress, which every caller that asks while it runs waits for.
type Refresh =
  | { status: typeof RefreshStatus.IDLE }
  | { status: typeof RefreshStatus.REFRESHING; refreshed: Promise<boolean> };

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

const SUCCEEDED: ApiResult<void> = { ok: true, body: undefined };
const NOT_SIGNED_IN: ApiResult<never> = {
  ok: false,
  failure: { kind: FailureKind.REFUSED, code: ErrorCode.NOT_SIGNED_IN, reason: "Sign in to continue." },
};
const SIGNED_OUT: Session = { status: SessionStatus.SIGNED_OUT };
const IDLE: Refresh = { status: RefreshStatus.IDLE };

const [session, setSession] = createSignal<Session>(SIGNED_OUT);
let refresh: Refresh = IDLE;

/**
 * @returns the signed-in user, or null when nobody is signed in
 */
export function currentUser(): CurrentUserResponse | null {
  const current = session();
  switch (current.status) {
    case SessionStatus.SIGNED_OUT:
      return null;
    case SessionStatus.SIGNED_IN:
      return current.user;
  }
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
  if (!tokens.ok) {
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
  if (current.status === SessionStatus.SIGNED_OUT) {
    return NOT_SIGNED_IN;
  }
  const changed = await sendAsCurrentUser((accessToken) => api.changePassword(accessToken, request));
  if (!changed.ok) {
    return changed;
  }
  return logIn({ username: current.user.username, password: request.newPassword });
}

export function hasPermission(permission: Permission): boolean {
  return currentUser()?.permissions.includes(permission) ?? false;
}

/**
 * Calls Sona as the current user. An expired access token is refreshed once and the call made again.
 */
export async function sendAsCurrentUser<T>(call: (accessToken: string) => Promise<ApiResult<T>>): Promise<ApiResult<T>> {
  const result = await sendWithSession(call);
  if (result.ok || !isNotSignedIn(result.failure)) {
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
  switch (current.status) {
    case SessionStatus.SIGNED_OUT:
      return NOT_SIGNED_IN;
    case SessionStatus.SIGNED_IN:
      return call(current.accessToken);
  }
}

function isNotSignedIn(failure: ApiFailure): boolean {
  return failure.kind === FailureKind.REFUSED && failure.code === ErrorCode.NOT_SIGNED_IN;
}

/**
 * Swaps the refresh cookie for new tokens, once at a time however many callers ask.
 *
 * @returns whether the session is still going
 */
async function refreshSession(): Promise<boolean> {
  const refreshed = await runningRefresh();
  refresh = IDLE;
  return refreshed;
}

/**
 * @returns the refresh in progress, starting one if none is
 */
function runningRefresh(): Promise<boolean> {
  if (refresh.status === RefreshStatus.REFRESHING) {
    return refresh.refreshed;
  }
  const refreshed = refreshSessionLocked();
  refresh = { status: RefreshStatus.REFRESHING, refreshed };
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
    if (!tokens.ok) {
      const failure = tokens.failure;
      if (failure.kind === FailureKind.REFUSED && failure.code === ErrorCode.INVALID_REFRESH_TOKEN) {
        endSession();
      }
      return false;
    }
    const started = await startSession(tokens.body);
    return started.ok;
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
  if (!user.ok) {
    return user;
  }
  stopRefreshTimer();
  // A minute early, or halfway through for tokens that last less than two minutes.
  const refreshIn = Math.max(tokens.expiresIn - 60, tokens.expiresIn / 2);
  const refreshTimer = setTimeout(refreshSession, refreshIn * 1000);
  setSession({ status: SessionStatus.SIGNED_IN, accessToken: tokens.accessToken, user: user.body, refreshTimer });
  return SUCCEEDED;
}

function endSession() {
  stopRefreshTimer();
  setSession(SIGNED_OUT);
}

function stopRefreshTimer() {
  const current = session();
  if (current.status === SessionStatus.SIGNED_IN) {
    clearTimeout(current.refreshTimer);
  }
}
