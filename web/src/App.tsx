import { Navigate, Route, Router, type RouteSectionProps } from "@solidjs/router";
import { Show } from "solid-js";
import { user } from "./auth";
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
import Login from "./pages/Login";
import PlaylistDetail from "./pages/PlaylistDetail";
import Playlists from "./pages/Playlists";
import ReleaseDetail from "./pages/ReleaseDetail";
import ReleaseGroupDetail from "./pages/ReleaseGroupDetail";
import Settings from "./pages/Settings";

function ListeningLayout(props: RouteSectionProps) {
  return (
    <Show when={user()} fallback={<Navigate href="/login" />}>
      <div class="app">
        <TopBar />
        <main class="page">{props.children}</main>
        <PlayerBar />
        <PlaylistDialog />
        <Toast />
      </div>
    </Show>
  );
}

export default function App() {
  return (
    <Router>
      <Route path="/login" component={Login} />
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
  );
}
