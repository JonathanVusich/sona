import { Navigate } from "@solidjs/router";
import { createSignal, Show } from "solid-js";
import { currentUser, logIn } from "../auth";
import { AppRoutes } from "../routes";

export default function Login() {
  const [username, setUsername] = createSignal("");
  const [password, setPassword] = createSignal("");
  const [error, setError] = createSignal<string | null>(null);
  const [pending, setPending] = createSignal(false);

  // Once logged in, the fallback below leaves this page.
  const submit = async (event: SubmitEvent) => {
    event.preventDefault();
    setPending(true);
    const result = await logIn({ username: username().trim(), password: password() });
    setPending(false);
    setError(result.ok ? null : result.failure.reason);
  };

  return (
    <Show when={!currentUser()} fallback={<Navigate href={AppRoutes.HOME} />}>
      <div class="login">
        <form class="login-card" onSubmit={submit}>
          <h1 class="brand">sona</h1>
          <label>
            Username
            <input
              value={username()}
              onInput={(e) => setUsername(e.currentTarget.value)}
              autocomplete="username"
              autofocus
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
