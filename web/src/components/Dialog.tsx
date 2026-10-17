import { onMount, type JSX } from "solid-js";

// A native modal dialog, shown for as long as it's rendered, so forms in it start fresh each time it opens. Closing it,
// with Escape, the backdrop or a button, asks its parent to stop rendering it.
export default function Dialog(props: { title: string; onClose: () => void; children: JSX.Element }) {
  let element: HTMLDialogElement | undefined;

  onMount(() => element?.showModal());

  const closeOnBackdrop = (event: MouseEvent) => {
    // The dialog element itself is only hit on the backdrop; the content sits in an inner box.
    if (event.target === event.currentTarget) {
      props.onClose();
    }
  };

  return (
    <dialog ref={element} class="dialog" onClose={() => props.onClose()} onClick={closeOnBackdrop}>
      <div class="dialog-body">
        <h2 class="dialog-title">{props.title}</h2>
        {props.children}
      </div>
    </dialog>
  );
}

export function ConfirmDialog(props: {
  title: string;
  message: string;
  confirmLabel: string;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <Dialog title={props.title} onClose={props.onClose}>
      <p class="dialog-message">{props.message}</p>
      <div class="dialog-actions">
        <button onClick={() => props.onClose()}>Cancel</button>
        <button
          class="primary destructive"
          onClick={() => {
            props.onConfirm();
            props.onClose();
          }}
        >
          {props.confirmLabel}
        </button>
      </div>
    </Dialog>
  );
}
