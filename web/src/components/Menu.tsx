import { createSignal, Show, type JSX } from "solid-js";

// At most one menu is open at a time, so one pair of document listeners serves every menu on the page.
const [openMenu, setOpenMenu] = createSignal<symbol | null>(null);

document.addEventListener("click", (event) => {
  if (openMenu() !== null && !(event.target as Element).closest(".menu[data-open]")) {
    setOpenMenu(null);
  }
});
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape") {
    setOpenMenu(null);
  }
});

export default function Menu(props: { label: string; icon: JSX.Element; class?: string; children: JSX.Element }) {
  const id = Symbol(props.label);
  const open = () => openMenu() === id;

  return (
    <div class={`menu ${props.class ?? ""}`} data-open={open() ? "" : undefined}>
      <button
        class="icon-button"
        aria-label={props.label}
        aria-haspopup="menu"
        aria-expanded={open()}
        onClick={() => setOpenMenu(open() ? null : id)}
      >
        {props.icon}
      </button>
      <Show when={open()}>
        <div class="menu-panel" role="menu" onClick={() => setOpenMenu(null)}>
          {props.children}
        </div>
      </Show>
    </div>
  );
}
