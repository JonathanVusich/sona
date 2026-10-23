// The paths of Sona's HTTP endpoints, as ApiRoutes names them on the server.
export const ApiRoutes = {
  AUTH_TOKEN: "/api/auth/token",
  AUTH_REFRESH: "/api/auth/refresh",
  AUTH_LOGOUT: "/api/auth/logout",
  CURRENT_USER: "/api/users/me",
  CURRENT_USER_PASSWORD: "/api/users/me/password",
  RELEASES: "/api/releases",
  RELEASE_GROUPS: "/api/release-groups",
  ARTISTS: "/api/artists",
  INGEST_GROUPS: "/api/ingest-groups",
  // STOMP over WebSocket.
  SOCKET: "/api/socket",
  // Where an uploader subscribes for each new state of the ingests in the groups they created.
  INGEST_PROGRESS: "/user/queue/ingests",
} as const;
