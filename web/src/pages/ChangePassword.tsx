import { Navigate } from "@solidjs/router";
import { createSignal, Show } from "solid-js";
import { SessionState } from "../api/responses";
import { changePassword, currentUser, logOut } from "../auth";

// Users who must change their password (such as the first admin) land here, and can do nothing else until they do.
// Once it is changed, here or in another tab, the fallback leaves this page.
export default function ChangePassword() {
  const [currentPassword, setCurrentPassword] = createSignal("");
  const [newPassword, setNewPassword] = createSignal("");
  const [confirmedPassword, setConfirmedPassword] = createSignal("");
  const [error, setError] = createSignal<string | null>(null);
  const [pending, setPending] = createSignal(false);

  const submit = async (event: SubmitEvent) => {
    event.preventDefault();
    if (newPassword() !== confirmedPassword()) {
      setError("The new passwords don't match.");
      return;
    }
    setPending(true);
    const result = await changePassword({ currentPassword: currentPassword(), newPassword: newPassword() });
    setPending(false);
    setError(result.ok ? null : result.failure.reason);
  };

  return (
    <Show when={currentUser()?.state === SessionState.PASSWORD_CHANGE_REQUIRED} fallback={<Navigate href="/" />}>
      <div class="login">
        <form class="login-card" onSubmit={submit}>
          <h1 class="brand">sona</h1>
          <p class="hint">Choose a new password to continue.</p>
          <label>
            Current password
            <input
              type="password"
              value={currentPassword()}
              onInput={(e) => setCurrentPassword(e.currentTarget.value)}
              autocomplete="current-password"
              autofocus
              required
            />
          </label>
          <label>
            New password
            <input
              type="password"
              value={newPassword()}
              onInput={(e) => setNewPassword(e.currentTarget.value)}
              autocomplete="new-password"
              required
            />
          </label>
          <label>
            Confirm new password
            <input
              type="password"
              value={confirmedPassword()}
              onInput={(e) => setConfirmedPassword(e.currentTarget.value)}
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
          <button type="button" class="link" onClick={logOut}>
            Log out
          </button>
        </form>
      </div>
    </Show>
  );
}
