import { createSignal } from "solid-js";

// Mocked auth: accounts live in the browser until the backend has real security.

export type Role = "user" | "admin";
export type Permission = "read" | "write" | "upload";

export interface User {
  name: string;
  role: Role;
}

const ACCOUNTS: Record<string, { password: string; role: Role }> = {
  user: { password: "user", role: "user" },
  admin: { password: "admin", role: "admin" },
};

const PERMISSIONS: Record<Role, Permission[]> = {
  user: ["read"],
  admin: ["read", "write", "upload"],
};

const STORAGE_KEY = "sona.user";

const [user, setUser] = createSignal<User | null>(loadUser());

export { user };

export function login(name: string, password: string): boolean {
  const account = ACCOUNTS[name];
  if (account === undefined || account.password !== password) {
    return false;
  }
  const loggedIn: User = { name, role: account.role };
  setUser(loggedIn);
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(loggedIn));
  } catch {
    // Storage unavailable; the login still holds for this page load.
  }
  return true;
}

export function logout() {
  setUser(null);
  try {
    sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // Nothing to clear.
  }
}

export function permissionsOf(role: Role): Permission[] {
  return PERMISSIONS[role];
}

export function can(permission: Permission): boolean {
  const current = user();
  return current !== null && PERMISSIONS[current.role].includes(permission);
}

function loadUser(): User | null {
  try {
    const stored = sessionStorage.getItem(STORAGE_KEY);
    return stored === null ? null : (JSON.parse(stored) as User);
  } catch {
    return null;
  }
}
