import type { JSX, ParentProps } from "solid-js";

// A list page's heading. Its children, a PageMeta line and a PageAction, sit under and beside the title.
export function PageHeader(props: ParentProps<{ title: string }>) {
  return (
    <header class="page-header">
      <h1>{props.title}</h1>
      {props.children}
    </header>
  );
}

export function PageMeta(props: { children: JSX.Element }) {
  return <p class="page-meta">{props.children}</p>;
}

export function PageAction(props: { children: JSX.Element }) {
  return <div class="page-action">{props.children}</div>;
}

export default function Section(props: { title: string; children: JSX.Element }) {
  return (
    <section class="section">
      <h2 class="section-title">{props.title}</h2>
      {props.children}
    </section>
  );
}
