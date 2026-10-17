import { createSignal, Show, type JSX } from "solid-js";
import { GearIcon, MoreIcon } from "./Icons";

// What a menu is for, which sets its button's icon and how it's styled.
export const MenuKind = {
  // The account menu in the top bar.
  SETTINGS: "settings",
  // A track row's menu, shown when the row is hovered.
  TRACK: "track",
  // A page's further actions, beside its Play and Shuffle buttons.
  ACTIONS: "actions",
} as const;

export type MenuKind = (typeof MenuKind)[keyof typeof MenuKind];

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

export default function Menu(props: { kind: MenuKind; label: string; children: JSX.Element }) {
  const id = Symbol(props.label);
  const open = () => openMenu() === id;

  return (
    <div class={`menu ${props.kind}-menu`} classList={{ open: open() }}>
      <button
        class="icon-button"
        aria-label={props.label}
        aria-haspopup="menu"
        aria-expanded={open()}
        onClick={() => toggleMenu(id)}
      >
        <MenuIcon kind={props.kind} />
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

function MenuIcon(props: { kind: MenuKind }) {
  return <>{menuIcon(props.kind)}</>;
}

function menuIcon(kind: MenuKind): JSX.Element {
  switch (kind) {
    case MenuKind.SETTINGS:
      return <GearIcon size={20} />;
    case MenuKind.TRACK:
    case MenuKind.ACTIONS:
      return <MoreIcon size={18} />;
  }
}
