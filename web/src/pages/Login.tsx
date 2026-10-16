import { Navigate } from "@solidjs/router";
import { createSignal, onMount, Show } from "solid-js";
import { currentUser, logIn } from "../auth";
import FormError from "../components/FormError";
import { EDITING, FormStatus, SUBMITTING, submittedForm, type FormState } from "../forms";
import { AppRoutes } from "../routes";

export default function Login() {
  const [username, setUsername] = createSignal("");
  const [password, setPassword] = createSignal("");
  const [form, setForm] = createSignal<FormState>(EDITING);
  let usernameInput: HTMLInputElement | undefined;

  // Focused here rather than with autofocus, which WebKit applies late enough to move focus away from a field already
  // being typed in.
  onMount(() => usernameInput?.focus());

  // Once logged in, the fallback below leaves this page.
  const submit = async (event: SubmitEvent) => {
    event.preventDefault();
    setForm(SUBMITTING);
    const result = await logIn({ username: username().trim(), password: password() });
    setForm(submittedForm(result));
  };

  return (
    <Show when={!currentUser()} fallback={<Navigate href={AppRoutes.HOME} />}>
      <div class="login">
        <form class="login-card" onSubmit={submit}>
          <h1 class="brand">sona</h1>
          <label>
            Username
            <input
              ref={usernameInput}
              value={username()}
              onInput={(e) => setUsername(e.currentTarget.value)}
              autocomplete="username"
              required
            />
          </label>
          <label>
            Password
            <input
              type="password"
              value={password()}
              onInput={(e) => setPassword(e.currentTarget.value)}
              autocomplete="current-password"
              required
            />
          </label>
          <FormError form={form()} />
          <button type="submit" class="primary" disabled={form().status === FormStatus.SUBMITTING}>
            Log in
          </button>
        </form>
      </div>
    </Show>
  );
}
