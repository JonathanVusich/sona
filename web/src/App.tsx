import { Navigate, Route, Router, type RouteSectionProps } from "@solidjs/router";
import { Match, Show, Switch } from "solid-js";
import { ready, user } from "./auth";
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

function ListeningLayout(props: RouteSectionProps) {
  return (
    <Switch>
      <Match when={!user()}>
        <Navigate href="/login" />
      </Match>
      <Match when={user()!.state === "PASSWORD_CHANGE_REQUIRED"}>
        <Navigate href="/change-password" />
      </Match>
      <Match when={user()}>
        <div class="app">
          <TopBar />
          <main class="page">{props.children}</main>
          <PlayerBar />
          <PlaylistDialog />
          <Toast />
        </div>
      </Match>
    </Switch>
  );
}

export default function App() {
  return (
    // Nothing is shown until the session from an earlier visit is restored, so a reload doesn't flash the login page.
    <Show when={ready()}>
      <Router>
        <Route path="/login" component={Login} />
        <Route path="/change-password" component={ChangePassword} />
        <Route path="/" component={ListeningLayout}>
          <Route path="/" component={() => <Navigate href="/discover" />} />
          <Route path="/albums" component={Albums} />
          <Route path="/artists" component={Artists} />
          <Route path="/artists/:id" component={ArtistDetail} />
          <Route path="/release-groups/:id" component={ReleaseGroupDetail} />
          <Route path="/releases/:id" component={ReleaseDetail} />
          <Route path="/playlists" component={Playlists} />
          <Route path="/playlists/:id" component={PlaylistDetail} />
          <Route path="/discover" component={Discover} />
          <Route path="/settings" component={Settings} />
          <Route path="/admin" component={() => <Navigate href="/admin/library" />} />
          <Route path="/admin/library" component={AdminLibrary} />
          <Route path="/admin/upload" component={AdminUpload} />
        </Route>
        <Route path="*" component={() => <Navigate href="/" />} />
      </Router>
    </Show>
  );
}
