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
  // Followed by a track's ID, and then by one of the paths below.
  TRACKS: "/api/tracks",
  TRACK_STREAM_TOKEN: "/stream-token",
  // Takes the track's stream token as the access_token parameter.
  TRACK_STREAM: "/stream",
  // Followed by a playlist's ID, and then by the paths below for its tracks.
  PLAYLISTS: "/api/playlists",
  // Followed by a track's ID, and then by PLAYLIST_TRACK_POSITION to move it.
  PLAYLIST_TRACKS: "/tracks",
  PLAYLIST_TRACK_POSITION: "/position",
  INGEST_GROUPS: "/api/ingest-groups",
  // STOMP over WebSocket.
  SOCKET: "/api/socket",
  // Where an uploader subscribes for each new state of the ingests in the groups they created.
  INGEST_PROGRESS: "/user/queue/ingests",
} as const;
