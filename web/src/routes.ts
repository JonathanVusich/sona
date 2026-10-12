// The frontend's own pages, as ApiRoutes names Sona's endpoints. A route with an ID is filled in with routeTo.

const ID_PARAMETER = ":id";

export const AppRoutes = {
  HOME: "/",
  LOGIN: "/login",
  CHANGE_PASSWORD: "/change-password",
  DISCOVER: "/discover",
  ALBUMS: "/albums",
  ARTISTS: "/artists",
  ARTIST: `/artists/${ID_PARAMETER}`,
  RELEASE_GROUP: `/release-groups/${ID_PARAMETER}`,
  RELEASE: `/releases/${ID_PARAMETER}`,
  PLAYLISTS: "/playlists",
  PLAYLIST: `/playlists/${ID_PARAMETER}`,
  SETTINGS: "/settings",
  ADMIN: "/admin",
  ADMIN_PAGES: "/admin/*",
  ADMIN_LIBRARY: "/admin/library",
  ADMIN_UPLOAD: "/admin/upload",
  // Matches any path no other route does.
  UNKNOWN: "*",
} as const;

export type AppRoute = (typeof AppRoutes)[keyof typeof AppRoutes];

/**
 * @returns the path of the route's page for the item with the ID
 */
export function routeTo(route: AppRoute, id: string): string {
  return route.replace(ID_PARAMETER, id);
}
