import { createSignal, Show } from "solid-js";

// Null while no toast is shown.
const [message, setMessage] = createSignal<string | null>(null);
// Hides the toast, while one is shown; a newer toast replaces it.
let hideTimer: number | null = null;

export function toast(text: string) {
  setMessage(text);
  if (hideTimer !== null) {
    window.clearTimeout(hideTimer);
  }
  hideTimer = window.setTimeout(() => {
    setMessage(null);
    hideTimer = null;
  }, 2400);
}

export default function Toast() {
  // Always rendered so screen readers announce changes to it.
  return (
    <div class="toast" classList={{ visible: message() !== null }} role="status">
      <Show when={message()}>{(shown) => shown()}</Show>
    </div>
  );
}
