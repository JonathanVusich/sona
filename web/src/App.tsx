import { Navigate, Route, Router, type RouteSectionProps } from "@solidjs/router";
import { Show } from "solid-js";
import { SessionState } from "./api/responses";
import { currentUser } from "./auth";
import PlayerBar from "./components/PlayerBar";
import PlaylistDialog from "./components/PlaylistDialog";
import Toast from "./components/Toast";
import TopBar from "./components/TopBar";
import AdminLibrary from "./pages/admin/Library";
import AdminUpload from "./pages/admin/Upload";
import Albums from "./pages/Albums";
import ArtistDetail from "./pages/ArtistDetail";
import Artists from "./pages/Artists";
import Discover from "./pages/Discover";
import ChangePassword from "./pages/ChangePassword";
import Login from "./pages/Login";
import PlaylistDetail from "./pages/PlaylistDetail";
import Playlists from "./pages/Playlists";
import ReleaseDetail from "./pages/ReleaseDetail";
import ReleaseGroupDetail from "./pages/ReleaseGroupDetail";
import Settings from "./pages/Settings";
import { AppRoutes } from "./routes";

function ListeningLayout(props: RouteSectionProps) {
  return (
    <Show when={currentUser()} fallback={<Navigate href={AppRoutes.LOGIN} />}>
      {(user) => (
        <Show when={user().state === SessionState.ACTIVE} fallback={<Navigate href={AppRoutes.CHANGE_PASSWORD} />}>
          <div class="app">
            <TopBar />
            <main class="page">{props.children}</main>
            <PlayerBar />
            <PlaylistDialog />
            <Toast />
          </div>
        </Show>
      )}
    </Show>
  );
}

export default function App() {
  return (
    <Router>
      <Route path={AppRoutes.LOGIN} component={Login} />
      <Route path={AppRoutes.CHANGE_PASSWORD} component={ChangePassword} />
      <Route path={AppRoutes.HOME} component={ListeningLayout}>
        <Route path={AppRoutes.HOME} component={() => <Navigate href={AppRoutes.DISCOVER} />} />
        <Route path={AppRoutes.ALBUMS} component={Albums} />
        <Route path={AppRoutes.ARTISTS} component={Artists} />
        <Route path={AppRoutes.ARTIST} component={ArtistDetail} />
        <Route path={AppRoutes.RELEASE_GROUP} component={ReleaseGroupDetail} />
        <Route path={AppRoutes.RELEASE} component={ReleaseDetail} />
        <Route path={AppRoutes.PLAYLISTS} component={Playlists} />
        <Route path={AppRoutes.PLAYLIST} component={PlaylistDetail} />
        <Route path={AppRoutes.DISCOVER} component={Discover} />
        <Route path={AppRoutes.SETTINGS} component={Settings} />
        <Route path={AppRoutes.ADMIN} component={() => <Navigate href={AppRoutes.ADMIN_LIBRARY} />} />
        <Route path={AppRoutes.ADMIN_LIBRARY} component={AdminLibrary} />
        <Route path={AppRoutes.ADMIN_UPLOAD} component={AdminUpload} />
      </Route>
      <Route path={AppRoutes.UNKNOWN} component={() => <Navigate href={AppRoutes.HOME} />} />
    </Router>
  );
}
