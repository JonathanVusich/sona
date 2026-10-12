import { Navigate, useNavigate } from "@solidjs/router";
import { createSignal, Show } from "solid-js";
import { ApiError } from "../api";
import { changePassword, logout, user } from "../auth";

// Users who must change their password (such as the first admin) land here, and can do nothing else until they do.
export default function ChangePassword() {
  const [current, setCurrent] = createSignal("");
  const [next, setNext] = createSignal("");
  const [confirmed, setConfirmed] = createSignal("");
  const [error, setError] = createSignal<string | null>(null);
  const [pending, setPending] = createSignal(false);
  const navigate = useNavigate();

  const submit = async (event: SubmitEvent) => {
    event.preventDefault();
    if (next() !== confirmed()) {
      setError("The new passwords don't match.");
      return;
    }
    setPending(true);
    try {
      await changePassword(current(), next());
      navigate("/", { replace: true });
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : "Something went wrong. Try again.");
    } finally {
      setPending(false);
    }
  };

  return (
    <Show when={user()} fallback={<Navigate href="/login" />}>
      <div class="login">
        <form class="login-card" onSubmit={submit}>
          <h1 class="brand">sona</h1>
          <p class="hint">Choose a new password to continue.</p>
          <label>
            Current password
            <input
              type="password"
              value={current()}
              onInput={(e) => setCurrent(e.currentTarget.value)}
              autocomplete="current-password"
              autofocus
              required
            />
          </label>
          <label>
            New password
            <input
              type="password"
              value={next()}
              onInput={(e) => setNext(e.currentTarget.value)}
              autocomplete="new-password"
              required
            />
          </label>
          <label>
            Confirm new password
            <input
              type="password"
              value={confirmed()}
              onInput={(e) => setConfirmed(e.currentTarget.value)}
              autocomplete="new-password"
              required
            />
          </label>
          <Show when={error()}>
            <p class="error">{error()}</p>
          </Show>
          <button type="submit" class="primary" disabled={pending()}>
            Change password
          </button>
          <button type="button" class="link" onClick={() => void logout()}>
            Log out
          </button>
        </form>
      </div>
    </Show>
  );
}
