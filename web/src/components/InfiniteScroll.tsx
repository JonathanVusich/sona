import { onCleanup, onMount } from "solid-js";
import type { PagedList } from "../paging";

// How far below the visible part of the page, in pixels, the end of a list starts loading its next page.
const LOAD_AHEAD = 600;

// Goes at the end of a list Sona serves a page at a time, and loads the next page whenever the end comes near while
// there's more to load, one page at a time. It keeps loading while the end stays in reach, so a tall window fills.
export default function InfiniteScroll(props: { list: PagedList<unknown> }) {
  let end: HTMLDivElement | undefined;
  // Set while pages load, so the observer reporting again doesn't start a second round.
  let loading = false;

  // Pages scroll inside their body rather than the window.
  const scroller = () => end?.closest(".page-body") ?? null;

  // Measured rather than taken from the observer's last report, which lags behind a page that has just loaded.
  const endInReach = () => {
    if (end === undefined) {
      return false;
    }
    const visibleBottom = scroller()?.getBoundingClientRect().bottom ?? window.innerHeight;
    return end.getBoundingClientRect().top <= visibleBottom + LOAD_AHEAD;
  };

  const loadWhileInReach = async () => {
    if (loading) {
      return;
    }
    loading = true;
    while (props.list.next() !== null && endInReach()) {
      await props.list.loadNextPage();
    }
    loading = false;
  };

  onMount(() => {
    const observer = new IntersectionObserver(
      async (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          await loadWhileInReach();
        }
      },
      { root: scroller(), rootMargin: `0px 0px ${LOAD_AHEAD}px 0px` },
    );
    if (end !== undefined) {
      observer.observe(end);
    }
    onCleanup(() => observer.disconnect());
  });

  return <div ref={end} class="infinite-scroll-end" aria-hidden="true" />;
}
