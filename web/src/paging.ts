import { createSignal, type Accessor } from "solid-js";
import { ResultKind, type ApiResult } from "./api/client";
import { PagePosition, type PageRequest } from "./api/requests";

// Lists Sona serves a page at a time, loaded as the user scrolls (InfiniteScroll).

/** A list as far as it has loaded. */
export interface PagedList<T> {
  items: Accessor<T[]>;
  /** The page to load next, or null once the last page has loaded or loading failed. */
  next: Accessor<PageRequest | null>;
  /** Why loading the list failed, or null while it hasn't. */
  failure: Accessor<string | null>;
  loadNextPage: () => Promise<void>;
}

/** What every page Sona serves has: the ID to load the next page after, or null on the last page. */
interface Page {
  next: string | null;
}

/**
 * @param fetchPage loads one page of the list
 * @param pageItems the list's items on a page
 */
export function createPagedList<T, P extends Page>(
  fetchPage: (page: PageRequest) => Promise<ApiResult<P>>,
  pageItems: (page: P) => T[],
): PagedList<T> {
  const [items, setItems] = createSignal<T[]>([]);
  const [next, setNext] = createSignal<PageRequest | null>({ position: PagePosition.FIRST });
  const [failure, setFailure] = createSignal<string | null>(null);

  const loadNextPage = async () => {
    const page = next();
    if (page === null) {
      return;
    }
    const result = await fetchPage(page);
    if (result.kind !== ResultKind.OK) {
      // Stop rather than retry without end.
      setFailure(result.reason);
      setNext(null);
      return;
    }
    setItems((loaded) => [...loaded, ...pageItems(result.body)]);
    setNext(pageAfter(result.body.next));
  };

  return { items, next, failure, loadNextPage };
}

function pageAfter(next: string | null): PageRequest | null {
  if (next === null) {
    return null;
  }
  return { position: PagePosition.AFTER, after: next };
}
