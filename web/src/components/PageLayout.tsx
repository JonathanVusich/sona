import { useLocation } from "@solidjs/router";
import { createEffect, on, type JSX } from "solid-js";

// A page whose header stays put while only the content below it scrolls.
export default function PageLayout(props: { header: JSX.Element; narrow?: boolean; children?: JSX.Element }) {
  const location = useLocation();
  let body!: HTMLDivElement;

  // One page component can show different items, such as moving between releases, so start each at the top.
  createEffect(on(() => location.pathname, () => body.scrollTo(0, 0), { defer: true }));

  return (
    <>
      <div class="page-head">
        <div class="page-inner" classList={{ narrow: props.narrow === true }}>
          {props.header}
        </div>
      </div>
      <div class="page-body" ref={body}>
        <div class="page-inner" classList={{ narrow: props.narrow === true }}>
          {props.children}
        </div>
      </div>
    </>
  );
}
