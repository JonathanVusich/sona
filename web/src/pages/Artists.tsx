import { Show } from "solid-js";
import { ArtistGrid } from "../components/Cards";
import PageLayout from "../components/PageLayout";
import { PageHeader } from "../components/Section";
import { plural } from "../format";
import { artists, artistsByName } from "../library";

export default function Artists() {
  return (
    <PageLayout header={<PageHeader title="Artists" meta={plural(artists().length, "artist")} />}>
      <Show when={artists().length > 0} fallback={<p class="empty">The library is empty.</p>}>
        <ArtistGrid artists={artistsByName()} />
      </Show>
    </PageLayout>
  );
}
