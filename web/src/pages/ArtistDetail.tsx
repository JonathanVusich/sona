import { useParams } from "@solidjs/router";
import { For, Show } from "solid-js";
import { ReleaseGroupGrid } from "../components/Cards";
import { ArtistAvatar } from "../components/Cover";
import { ShuffleIcon } from "../components/Icons";
import PageLayout from "../components/PageLayout";
import Section from "../components/Section";
import { plural } from "../format";
import { artistById, canonicalRelease, groupsOf, isAlbum, tracksOfRelease, type ReleaseGroup } from "../library";
import { shufflePlay } from "../player";

const SECTIONS: { title: string; include: (group: ReleaseGroup) => boolean }[] = [
  { title: "Albums", include: isAlbum },
  { title: "Singles & EPs", include: (group) => !isAlbum(group) },
];

export default function ArtistDetail() {
  const params = useParams<{ id: string }>();

  return (
    <Show when={artistById(params.id)} fallback={<PageLayout header={<p class="empty">This artist isn't in the library.</p>} />}>
      {(artist) => {
        const discography = () => groupsOf(artist().id);
        // Every song once: the original edition of each album, EP and single.
        const allTracks = () =>
          discography().flatMap((group) => {
            const release = canonicalRelease(group.id);
            return release === undefined ? [] : tracksOfRelease(release.id);
          });
        const albumCount = () => discography().filter(isAlbum).length;
        const counts = () => [
          albumCount() > 0 && plural(albumCount(), "album"),
          discography().length > albumCount() && plural(discography().length - albumCount(), "single or EP", "singles & EPs"),
        ];

        return (
          <PageLayout
            header={
              <header class="detail-header">
                <ArtistAvatar name={artist().name} class="detail-cover" />
                <div class="detail-info">
                  <span class="eyebrow">Artist</span>
                  <h1>{artist().name}</h1>
                  <p class="detail-meta">
                    {[artist().country, ...counts(), plural(allTracks().length, "song")].filter(Boolean).join(" · ")}
                  </p>
                  <div class="detail-actions">
                    <button class="primary" disabled={allTracks().length === 0} onClick={() => shufflePlay(allTracks())}>
                      <ShuffleIcon size={16} /> Shuffle
                    </button>
                  </div>
                </div>
              </header>
            }
          >
            <For each={SECTIONS}>
              {({ title, include }) => {
                const ofType = () => discography().filter(include);
                return (
                  <Show when={ofType().length > 0}>
                    <Section title={title}>
                      <ReleaseGroupGrid groups={ofType()} layout="grid" subtitle="year" />
                    </Section>
                  </Show>
                );
              }}
            </For>
          </PageLayout>
        );
      }}
    </Show>
  );
}
