import { createSignal } from "solid-js";
import { ApiError, ErrorCode, send } from "./api";

// The access token is kept in memory only. The refresh token is in an HttpOnly cookie that scripts can't read, so a
// reload restores the session by refreshing.

export type Role = "ADMIN" | "USER";
export type Permission = "LIBRARY_READ" | "LIBRARY_WRITE" | "LIBRARY_UPLOAD" | "USERS_MANAGE";
export type SessionState = "ACTIVE" | "PASSWORD_CHANGE_REQUIRED";

export interface User {
  userId: string;
  username: string;
  role: Role;
  state: SessionState;
  /** What the user may do now, which is nothing while they must change their password. */
  permissions: Permission[];
}

interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export const PERMISSION_LABELS: Record<Permission, string> = {
  LIBRARY_READ: "read",
  LIBRARY_WRITE: "write",
  LIBRARY_UPLOAD: "upload",
  USERS_MANAGE: "manage users",
};

const [user, setUser] = createSignal<User | null>(null);
// False until the session from an earlier visit has been restored or found to have ended.
const [ready, setReady] = createSignal(false);

export { ready, user };

let accessToken: string | null = null;
let refreshTimer: ReturnType<typeof setTimeout> | undefined;
let refreshing: Promise<boolean> | null = null;

// Tells the other tabs that the session changed, so they restore it from the shared cookie.
const tabs = "BroadcastChannel" in window ? new BroadcastChannel("sona-auth") : null;
if (tabs !== null) {
  tabs.onmessage = () => void restoreSession();
}

void restoreSession();

export async function login(username: string, password: string): Promise<void> {
  const tokens = await send<TokenResponse>("POST", "/api/auth/token", { body: { username, password } });
  await startSession(tokens);
  tabs?.postMessage("changed");
}

export async function logout(): Promise<void> {
  try {
    await send("POST", "/api/auth/logout");
  } catch {
    // The session has already ended, or Sona can't be reached; either way this tab forgets it.
  }
  endSession();
  tabs?.postMessage("changed");
}

/**
 * Changes the password, which ends every session the user has, then signs in again with the new one.
 */
export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  await request("POST", "/api/users/me/password", { currentPassword, newPassword });
  await login(user()!.username, newPassword);
}

export function can(permission: Permission): boolean {
  return user()?.permissions.includes(permission) ?? false;
}

/**
 * Sends a request as the signed-in user. An expired access token is refreshed once and the request sent again.
 */
export async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  try {
    return await send<T>(method, path, { token: accessToken ?? undefined, body });
  } catch (error) {
    if (error instanceof ApiError && error.code === ErrorCode.NOT_SIGNED_IN && (await refresh())) {
      return send<T>(method, path, { token: accessToken ?? undefined, body });
    }
    throw error;
  }
}

async function restoreSession() {
  await refresh();
  setReady(true);
}

/**
 * Swaps the refresh cookie for new tokens. Sona ends the whole session when a refresh token is used twice, so tabs
 * take turns: each one sends the cookie the tab before it was given.
 *
 * @returns whether the session is still going
 */
function refresh(): Promise<boolean> {
  refreshing ??= withRefreshLock(async () => {
    try {
      await startSession(await send<TokenResponse>("POST", "/api/auth/refresh"));
      return true;
    } catch (error) {
      if (error instanceof ApiError && error.code === ErrorCode.INVALID_REFRESH_TOKEN) {
        endSession();
      }
      return false;
    }
  }).finally(() => {
    refreshing = null;
  });
  return refreshing;
}

async function withRefreshLock(task: () => Promise<boolean>): Promise<boolean> {
  // Web Locks only exist in secure contexts, where the Secure refresh cookie works too.
  if (navigator.locks === undefined) {
    return task();
  }
  return await navigator.locks.request("sona-refresh", task);
}

async function startSession(tokens: TokenResponse) {
  accessToken = tokens.accessToken;
  clearTimeout(refreshTimer);
  // Refresh a minute early, or halfway through for tokens that last less than two minutes.
  const refreshIn = Math.max(tokens.expiresIn - 60, tokens.expiresIn / 2);
  refreshTimer = setTimeout(() => void refresh(), refreshIn * 1000);
  setUser(await send<User>("GET", "/api/users/me", { token: accessToken }));
}

function endSession() {
  accessToken = null;
  clearTimeout(refreshTimer);
  setUser(null);
}
