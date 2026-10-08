import { createSignal } from "solid-js";

const [message, setMessage] = createSignal<string | null>(null);
let hideTimer: number | undefined;

export function toast(text: string) {
  setMessage(text);
  window.clearTimeout(hideTimer);
  hideTimer = window.setTimeout(() => setMessage(null), 2400);
}

export default function Toast() {
  // Always rendered so screen readers announce changes to it.
  return (
    <div class="toast" classList={{ visible: message() !== null }} role="status">
      {message()}
    </div>
  );
}
