import { Show } from "solid-js";
import { ArtistGrid } from "../components/Cards";
import { PageBody, PageHead } from "../components/PageLayout";
import { PageHeader, PageMeta } from "../components/Section";
import { plural } from "../format";
import { artists, artistsByName } from "../library";

export default function Artists() {
  return (
    <>
      <PageHead>
        <PageHeader title="Artists">
          <PageMeta>{plural(artists().length, "artist")}</PageMeta>
        </PageHeader>
      </PageHead>
      <PageBody>
        <Show when={artists().length > 0} fallback={<p class="empty">The library is empty.</p>}>
          <ArtistGrid artists={artistsByName()} />
        </Show>
      </PageBody>
    </>
  );
}
