import { Show } from "solid-js";
import { CardLayout, ReleaseGroupGrid } from "../components/Cards";
import { PageBody, PageHead } from "../components/PageLayout";
import { PageHeader, PageMeta } from "../components/Section";
import { plural } from "../format";
import { groupsByArtist } from "../library";

export default function Albums() {
  // Everything in the library, singles and EPs included; artist pages separate them.
  const albums = () => groupsByArtist(() => true);
  return (
    <>
      <PageHead>
        <PageHeader title="Albums">
          <PageMeta>{plural(albums().length, "release")}</PageMeta>
        </PageHeader>
      </PageHead>
      <PageBody>
        <Show when={albums().length > 0} fallback={<p class="empty">No albums in the library yet.</p>}>
          <ReleaseGroupGrid groups={albums()} layout={CardLayout.GRID} />
        </Show>
      </PageBody>
    </>
  );
}
