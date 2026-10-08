import { Show } from "solid-js";
import { ReleaseGroupGrid } from "../components/Cards";
import PageLayout from "../components/PageLayout";
import { PageHeader } from "../components/Section";
import { plural } from "../format";
import { groupsByArtist } from "../library";

export default function Albums() {
  // Everything in the library, singles and EPs included; artist pages separate them.
  const albums = () => groupsByArtist(() => true);
  return (
    <PageLayout header={<PageHeader title="Albums" meta={plural(albums().length, "release")} />}>
      <Show when={albums().length > 0} fallback={<p class="empty">No albums in the library yet.</p>}>
        <ReleaseGroupGrid groups={albums()} layout="grid" subtitle="artist" />
      </Show>
    </PageLayout>
  );
}
