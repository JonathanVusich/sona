import { Navigate, useNavigate } from "@solidjs/router";
import { createSignal, Show } from "solid-js";
import { login, user } from "../auth";

export default function Login() {
  const [name, setName] = createSignal("");
  const [password, setPassword] = createSignal("");
  const [failed, setFailed] = createSignal(false);
  const navigate = useNavigate();

  const submit = (event: SubmitEvent) => {
    event.preventDefault();
    if (login(name().trim(), password())) {
      navigate("/", { replace: true });
    } else {
      setFailed(true);
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
          <Show when={failed()}>
            <p class="error">Wrong username or password.</p>
          </Show>
          <button type="submit" class="primary">
            Log in
          </button>
          <p class="muted hint">Mock accounts: user / user (read only), admin / admin (read, write, upload)</p>
        </form>
      </div>
    </Show>
  );
}
