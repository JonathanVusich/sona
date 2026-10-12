import { Show } from "solid-js";
import { CardLayout, CardSubtitle, ReleaseGroupGrid } from "../components/Cards";
import PageLayout from "../components/PageLayout";
import Section, { PageHeader } from "../components/Section";
import { notPlayedYet, recentlyAdded, recentlyPlayed } from "../library";

export default function Discover() {
  return (
    <PageLayout header={<PageHeader title="Discover" />}>
      <Section title="Recently played">
        <Show when={recentlyPlayed().length > 0} fallback={<p class="empty">Albums you play show up here.</p>}>
          <ReleaseGroupGrid groups={recentlyPlayed()} layout={CardLayout.ROW} subtitle={CardSubtitle.ARTIST} />
        </Show>
      </Section>
      <Section title="Recently added">
        <ReleaseGroupGrid groups={recentlyAdded().slice(0, 12)} layout={CardLayout.ROW} subtitle={CardSubtitle.ARTIST} />
      </Section>
      <Section title="Not played yet">
        <Show when={notPlayedYet().length > 0} fallback={<p class="empty">You've played everything in the library.</p>}>
          <ReleaseGroupGrid groups={notPlayedYet()} layout={CardLayout.ROW} subtitle={CardSubtitle.ARTIST} />
        </Show>
      </Section>
    </PageLayout>
  );
}
