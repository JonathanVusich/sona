import { Show } from "solid-js";
import { CardLayout, ReleaseGroupGrid } from "../components/Cards";
import { PageBody, PageHead } from "../components/PageLayout";
import Section, { PageHeader } from "../components/Section";
import { notPlayedYet, recentlyAdded, recentlyPlayed, summaryResponse } from "../library";

export default function Discover() {
  return (
    <>
      <PageHead>
        <PageHeader title="Discover" />
      </PageHead>
      <PageBody>
        <Section title="Recently played">
          <Show when={recentlyPlayed().length > 0} fallback={<p class="empty">Albums you play show up here.</p>}>
            <ReleaseGroupGrid groups={recentlyPlayed().map(summaryResponse)} layout={CardLayout.ROW} />
          </Show>
        </Section>
        <Section title="Recently added">
          <ReleaseGroupGrid groups={recentlyAdded().slice(0, 12).map(summaryResponse)} layout={CardLayout.ROW} />
        </Section>
        <Section title="Not played yet">
          <Show
            when={notPlayedYet().length > 0}
            fallback={<p class="empty">You've played everything in the library.</p>}
          >
            <ReleaseGroupGrid groups={notPlayedYet().map(summaryResponse)} layout={CardLayout.ROW} />
          </Show>
        </Section>
      </PageBody>
    </>
  );
}
