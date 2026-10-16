import { createSignal } from "solid-js";

const ToastStatus = {
  HIDDEN: "hidden",
  SHOWN: "shown",
} as const;

// A shown toast hides itself on a timer, which a newer toast replaces.
type ToastState =
  | { status: typeof ToastStatus.HIDDEN }
  | { status: typeof ToastStatus.SHOWN; message: string; hideTimer: number };

const HIDDEN: ToastState = { status: ToastStatus.HIDDEN };

const [shown, setShown] = createSignal<ToastState>(HIDDEN);

export function toast(text: string) {
  const current = shown();
  if (current.status === ToastStatus.SHOWN) {
    window.clearTimeout(current.hideTimer);
  }
  const hideTimer = window.setTimeout(() => setShown(HIDDEN), 2400);
  setShown({ status: ToastStatus.SHOWN, message: text, hideTimer });
}

export default function Toast() {
  // Always rendered so screen readers announce changes to it.
  return (
    <div class="toast" classList={{ visible: shown().status === ToastStatus.SHOWN }} role="status">
      {toastMessage(shown())}
    </div>
  );
}

function toastMessage(toast: ToastState): string {
  switch (toast.status) {
    case ToastStatus.HIDDEN:
      return "";
    case ToastStatus.SHOWN:
      return toast.message;
  }
}
