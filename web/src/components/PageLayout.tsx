import { useLocation } from "@solidjs/router";
import { createEffect, on, type JSX } from "solid-js";

// A page is a PageHead that stays put and a PageBody that scrolls beneath it, side by side in the layout's main.

export function PageHead(props: { children: JSX.Element }) {
  return (
    <div class="page-head">
      <div class="page-inner">{props.children}</div>
    </div>
  );
}

export function PageBody(props: { children: JSX.Element }) {
  const location = useLocation();
  let body: HTMLDivElement | undefined;

  // One page component can show different items, such as moving between releases, so start each at the top.
  createEffect(
    on(
      () => location.pathname,
      () => body?.scrollTo(0, 0),
      { defer: true },
    ),
  );

  return (
    <div class="page-body" ref={body}>
      <div class="page-inner">{props.children}</div>
    </div>
  );
}

// Keeps a page of short rows, such as settings, to a readable width.
export function NarrowPage(props: { children: JSX.Element }) {
  return <div class="narrow-page">{props.children}</div>;
}

// A page with only something to say, such as that what it would show isn't in the library.
export function PageMessage(props: { message: string }) {
  return (
    <PageHead>
      <p class="empty">{props.message}</p>
    </PageHead>
  );
}
