import { Navigate } from "@solidjs/router";
import { createSignal, Show } from "solid-js";
import { ApiError } from "../api";
import { login, user } from "../auth";

export default function Login() {
  const [name, setName] = createSignal("");
  const [password, setPassword] = createSignal("");
  const [error, setError] = createSignal<string | null>(null);
  const [pending, setPending] = createSignal(false);

  // Once signed in, the fallback below leaves this page.
  const submit = async (event: SubmitEvent) => {
    event.preventDefault();
    setPending(true);
    try {
      await login(name().trim(), password());
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : "Something went wrong. Try again.");
    } finally {
      setPending(false);
    }
  };

  return (
    <Show when={!user()} fallback={<Navigate href="/" />}>
      <div class="login">
        <form class="login-card" onSubmit={submit}>
          <h1 class="brand">sona</h1>
          <label>
            Username
            <input value={name()} onInput={(e) => setName(e.currentTarget.value)} autocomplete="username" autofocus required />
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
          <Show when={error()}>
            <p class="error">{error()}</p>
          </Show>
          <button type="submit" class="primary" disabled={pending()}>
            Log in
          </button>
        </form>
      </div>
    </Show>
  );
}
