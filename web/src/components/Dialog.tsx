import { createEffect, Show, type JSX } from "solid-js";

// A native modal dialog. Its content is only rendered while open, so forms start fresh each time.
export default function Dialog(props: { open: boolean; title: string; onClose: () => void; children: JSX.Element }) {
  let element!: HTMLDialogElement;

  createEffect(() => {
    if (props.open && !element.open) {
      element.showModal();
    } else if (!props.open && element.open) {
      element.close();
    }
  });

  return (
    <dialog
      ref={element}
      class="dialog"
      onClose={() => props.onClose()}
      // The dialog element itself is only hit on the backdrop; the content sits in an inner box.
      onClick={(event) => event.target === element && props.onClose()}
    >
      <Show when={props.open}>
        <div class="dialog-body">
          <h2 class="dialog-title">{props.title}</h2>
          {props.children}
        </div>
      </Show>
    </dialog>
  );
}

export function ConfirmDialog(props: {
  open: boolean;
  title: string;
  message: string;
  confirmLabel: string;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <Dialog open={props.open} title={props.title} onClose={props.onClose}>
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
