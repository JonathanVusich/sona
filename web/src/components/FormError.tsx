import type { JSX } from "solid-js";
import { FormStatus, type FormState } from "../forms";

// Why a form's request failed, when it did.
export default function FormError(props: { form: FormState }) {
  return <>{formError(props.form)}</>;
}

function formError(form: FormState): JSX.Element {
  switch (form.status) {
    case FormStatus.FAILED:
      return <p class="error">{form.reason}</p>;
    case FormStatus.EDITING:
    case FormStatus.SUBMITTING:
      return <></>;
  }
}
