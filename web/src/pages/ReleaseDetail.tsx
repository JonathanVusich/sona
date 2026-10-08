import { A, useParams } from "@solidjs/router";
import { Show } from "solid-js";
import { ReleaseGroupGrid } from "../components/Cards";
import Cover from "../components/Cover";
import EditionList from "../components/EditionList";
import { MoreIcon, PlayIcon, ShuffleIcon } from "../components/Icons";
import Menu from "../components/Menu";
import PageLayout from "../components/PageLayout";
import { AddToPlaylistItems } from "../components/PlaylistDialog";
import Section from "../components/Section";
import TrackList from "../components/TrackList";
import { formatTotal, plural } from "../format";
import { artistById, groupById, groupsOf, releaseById, releasesOf, tracksOfRelease } from "../library";
import { playTracks, shufflePlay } from "../player";

// One concrete edition: its own tracks and details, the other editions of the same release group, and the rest of
// the artist's discography.
export default function ReleaseDetail() {
  const params = useParams<{ id: string }>();

  return (
    <Show when={releaseById(params.id)} fallback={<PageLayout header={<p class="empty">This release isn't in the library.</p>} />}>
      {(release) => {
        const group = () => groupById(release().groupId);
        const artist = () => {
          const releaseGroup = group();
          return releaseGroup === undefined ? undefined : artistById(releaseGroup.artistId);
        };
        const tracks = () => tracksOfRelease(release().id);
        const total = () => tracks().reduce((sum, track) => sum + track.duration, 0);
        const editions = () => releasesOf(release().groupId);
        const moreByArtist = () => {
          const releaseGroup = group();
          return releaseGroup === undefined
            ? []
            : groupsOf(releaseGroup.artistId).filter((other) => other.id !== releaseGroup.id);
        };

        return (
          <PageLayout
            header={
              <header class="detail-header">
                <Cover seed={group()?.title ?? release().title} class="detail-cover" />
                <div class="detail-info">
                  <span class="eyebrow">Release</span>
                  <h1>{release().title}</h1>
                  <Show when={artist()}>
                    {(releaseArtist) => (
                      <A href={`/artists/${releaseArtist().id}`} class="detail-artist">
                        {releaseArtist().name}
                      </A>
                    )}
                  </Show>
                  <p class="detail-meta">
                    {[release().year, release().format, release().label, release().country, plural(tracks().length, "song"), formatTotal(total())]
                      .filter(Boolean)
                      .join(" · ")}
                  </p>
                  <Show when={group()}>
                    {(releaseGroup) => (
                      <p class="detail-meta">
                        Edition of <A href={`/release-groups/${releaseGroup().id}`}>{releaseGroup().title}</A>
                      </p>
                    )}
                  </Show>
                  <div class="detail-actions">
                    <button class="primary" onClick={() => playTracks(tracks())}>
                      <PlayIcon size={16} /> Play
                    </button>
                    <button onClick={() => shufflePlay(tracks())}>
                      <ShuffleIcon size={16} /> Shuffle
                    </button>
                    <Menu label="More" icon={<MoreIcon size={18} />}>
                      <AddToPlaylistItems trackIds={() => tracks().map((track) => track.id)} />
                    </Menu>
                  </div>
                </div>
              </header>
            }
          >
            <TrackList tracks={tracks()} showContext={false} onPlay={(index) => playTracks(tracks(), index)} />
            <Show when={editions().length > 1}>
              <Section title="Other editions">
                <EditionList releases={editions()} currentId={release().id} />
              </Section>
            </Show>
            <Show when={moreByArtist().length > 0}>
              <Section title={`More by ${artist()?.name ?? "this artist"}`}>
                <ReleaseGroupGrid groups={moreByArtist()} layout="row" subtitle="year" />
              </Section>
            </Show>
          </PageLayout>
        );
      }}
    </Show>
  );
}
