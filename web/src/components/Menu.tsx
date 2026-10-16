import { createSignal, Show, type JSX } from "solid-js";

// At most one menu is open at a time, so one pair of document listeners serves every menu on the page. Each menu has
// its own symbol, and NO_MENU stands for none being open.
const NO_MENU = Symbol("no menu");

const [openMenu, setOpenMenu] = createSignal<symbol>(NO_MENU);

document.addEventListener("click", (event) => {
  if (openMenu() !== NO_MENU && !(event.target instanceof Element && event.target.closest(".menu.open"))) {
    setOpenMenu(NO_MENU);
  }
});
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape") {
    setOpenMenu(NO_MENU);
  }
});

export default function Menu(props: { label: string; icon: JSX.Element; class?: string; children: JSX.Element }) {
  const id = Symbol(props.label);
  const open = () => openMenu() === id;

  return (
    <div class={`menu ${props.class ?? ""}`} classList={{ open: open() }}>
      <button
        class="icon-button"
        aria-label={props.label}
        aria-haspopup="menu"
        aria-expanded={open()}
        onClick={() => toggleMenu(id)}
      >
        {props.icon}
      </button>
      <Show when={open()}>
        <div class="menu-panel" role="menu" onClick={() => setOpenMenu(NO_MENU)}>
          {props.children}
        </div>
      </Show>
    </div>
  );
}

function toggleMenu(id: symbol) {
  if (openMenu() === id) {
    setOpenMenu(NO_MENU);
    return;
  }
  setOpenMenu(id);
}
