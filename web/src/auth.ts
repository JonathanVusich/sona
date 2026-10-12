import { createSignal } from "solid-js";
import * as api from "./api/client";
import { ClientError } from "./api/client";
import type { ChangePasswordRequest, LoginRequest } from "./api/requests";
import { ErrorCode, type CurrentUserResponse, type Permission, type TokenResponse } from "./api/responses";

// The access token is kept in memory only. The refresh token is in an HttpOnly cookie that scripts can't read, so a
// reload restores the session by refreshing it.

interface Session {
  accessToken: string;
  user: CurrentUserResponse;
}

export const PERMISSION_LABELS: Record<Permission, string> = {
  LIBRARY_READ: "read",
  LIBRARY_WRITE: "write",
  LIBRARY_UPLOAD: "upload",
  USERS_MANAGE: "manage users",
};

const [session, setSession] = createSignal<Session | null>(null);

export const currentUser = () => session()?.user ?? null;

let refreshTimer: ReturnType<typeof setTimeout> | undefined;
let pendingRefresh: Promise<boolean> | null = null;

// Tells the other tabs that the session changed, so they restore it from the cookie they share.
const otherTabs = new BroadcastChannel("sona-auth");
otherTabs.onmessage = restoreSession;

/**
 * Restores the session from an earlier visit, if it is still going.
 */
export async function restoreSession(): Promise<void> {
  await refreshSession();
}

export async function logIn(request: LoginRequest): Promise<void> {
  await startSession(await api.login(request));
  otherTabs.postMessage("changed");
}

export async function logOut(): Promise<void> {
  try {
    await api.logout();
  } catch {
    // The session has already ended, or Sona can't be reached; either way this tab forgets it.
  }
  endSession();
  otherTabs.postMessage("changed");
}

/**
 * Changes the password, which ends every session the user has, then logs in again with the new one.
 */
export async function changePassword(request: ChangePasswordRequest): Promise<void> {
  const username = requireSession().user.username;
  await sendAsCurrentUser((accessToken) => api.changePassword(accessToken, request));
  await logIn({ username, password: request.newPassword });
}

export function hasPermission(permission: Permission): boolean {
  return currentUser()?.permissions.includes(permission) ?? false;
}

/**
 * Calls Sona as the current user. An expired access token is refreshed once and the call made again.
 */
export async function sendAsCurrentUser<T>(call: (accessToken: string) => Promise<T>): Promise<T> {
  try {
    return await call(requireSession().accessToken);
  } catch (error) {
    if (error instanceof ClientError && error.code === ErrorCode.NOT_SIGNED_IN && (await refreshSession())) {
      return await call(requireSession().accessToken);
    }
    throw error;
  }
}

/**
 * Swaps the refresh cookie for new tokens, once at a time however many callers ask.
 *
 * @returns whether the session is still going
 */
async function refreshSession(): Promise<boolean> {
  pendingRefresh ??= refreshSessionLocked();
  try {
    return await pendingRefresh;
  } finally {
    pendingRefresh = null;
  }
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
  return await navigator.locks.request("sona-refresh", async () => {
    try {
      await startSession(await api.refreshTokens());
      return true;
    } catch (error) {
      if (error instanceof ClientError && error.code === ErrorCode.INVALID_REFRESH_TOKEN) {
        endSession();
      }
      return false;
    }
  });
}

async function startSession(tokens: TokenResponse): Promise<void> {
  const user = await api.fetchCurrentUser(tokens.accessToken);
  setSession({ accessToken: tokens.accessToken, user });
  clearTimeout(refreshTimer);
  // A minute early, or halfway through for tokens that last less than two minutes.
  const refreshIn = Math.max(tokens.expiresIn - 60, tokens.expiresIn / 2);
  refreshTimer = setTimeout(refreshSession, refreshIn * 1000);
}

function endSession() {
  clearTimeout(refreshTimer);
  setSession(null);
}

function requireSession(): Session {
  const current = session();
  if (current === null) {
    throw new ClientError(401, { code: ErrorCode.NOT_SIGNED_IN, reason: "Sign in to continue." });
  }
  return current;
}
