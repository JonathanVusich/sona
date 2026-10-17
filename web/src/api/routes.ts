// The paths of Sona's HTTP endpoints, as ApiRoutes names them on the server.
export const ApiRoutes = {
  AUTH_TOKEN: "/api/auth/token",
  AUTH_REFRESH: "/api/auth/refresh",
  AUTH_LOGOUT: "/api/auth/logout",
  CURRENT_USER: "/api/users/me",
  CURRENT_USER_PASSWORD: "/api/users/me/password",
  RELEASES: "/api/releases",
  RELEASE_GROUPS: "/api/release-groups",
} as const;
