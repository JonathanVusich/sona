import { Show } from "solid-js";
import type { PagedList } from "../paging";

// What a paged list says when it turns out to be empty, or when loading it failed.
export default function ListMessage(props: { list: PagedList<unknown>; emptyMessage: string }) {
  const empty = () => props.list.next() === null && props.list.failure() === null && props.list.items().length === 0;
  return (
    <>
      <Show when={empty()}>
        <p class="empty">{props.emptyMessage}</p>
      </Show>
      <Show when={props.list.failure()}>{(failure) => <p class="error">{failure()}</p>}</Show>
    </>
  );
}
