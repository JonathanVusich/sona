import type { JSX } from "solid-js";

export function PageHeader(props: { title: string; meta?: string; action?: JSX.Element }) {
  return (
    <header class="page-header">
      <div>
        <h1>{props.title}</h1>
        {props.meta && <p class="page-meta">{props.meta}</p>}
      </div>
      {props.action}
    </header>
  );
}

export default function Section(props: { title: string; children: JSX.Element }) {
  return (
    <section class="section">
      <h2 class="section-title">{props.title}</h2>
      {props.children}
    </section>
  );
}
