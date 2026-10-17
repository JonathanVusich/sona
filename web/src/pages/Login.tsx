import { Navigate } from "@solidjs/router";
import { createSignal, onMount, Show } from "solid-js";
import { currentUser, logIn } from "../auth";
import FormError from "../components/FormError";
import { failureReason, FormStatus } from "../forms";
import { AppRoutes } from "../routes";

export default function Login() {
  const [username, setUsername] = createSignal("");
  const [password, setPassword] = createSignal("");
  const [error, setError] = createSignal<string | null>(null);
  const [status, setStatus] = createSignal<FormStatus>(FormStatus.EDITING);
  let usernameInput: HTMLInputElement | undefined;

  // Focused here rather than with autofocus, which WebKit applies late enough to move focus away from a field already
  // being typed in.
  onMount(() => usernameInput?.focus());

  // Once logged in, the fallback below leaves this page.
  const submit = async (event: SubmitEvent) => {
    event.preventDefault();
    setStatus(FormStatus.SUBMITTING);
    const result = await logIn({ username: username().trim(), password: password() });
    setStatus(FormStatus.EDITING);
    setError(failureReason(result));
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
          <FormError error={error()} />
          <button type="submit" class="primary" disabled={status() === FormStatus.SUBMITTING}>
            Log in
          </button>
        </form>
      </div>
    </Show>
  );
}
