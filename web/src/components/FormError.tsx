import { Show } from "solid-js";

// Why a form's request failed, when it did.
export default function FormError(props: { error: string | null }) {
  return (
    <Show when={props.error}>
      <p class="error">{props.error}</p>
    </Show>
  );
}
