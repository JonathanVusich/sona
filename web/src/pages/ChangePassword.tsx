import { Navigate } from "@solidjs/router";
import { createSignal, onMount, Show } from "solid-js";
import { SessionState } from "../api/responses";
import { changePassword, currentUser, logOut } from "../auth";
import FormError from "../components/FormError";
import { failureReason, FormStatus, isBlank } from "../forms";
import { AppRoutes } from "../routes";

// Users who must change their password (such as the first admin) land here, and can do nothing else until they do.
// Once it is changed, here or in another tab, the fallback leaves this page.
export default function ChangePassword() {
  const [currentPassword, setCurrentPassword] = createSignal("");
  const [newPassword, setNewPassword] = createSignal("");
  const [confirmedPassword, setConfirmedPassword] = createSignal("");
  const [error, setError] = createSignal<string | null>(null);
  const [status, setStatus] = createSignal<FormStatus>(FormStatus.EDITING);
  let currentPasswordInput: HTMLInputElement | undefined;

  // Focused here rather than with autofocus, which WebKit applies late enough to move focus away from a field already
  // being typed in.
  onMount(() => currentPasswordInput?.focus());

  const submit = async (event: SubmitEvent) => {
    event.preventDefault();
    if (isBlank(currentPassword()) || isBlank(newPassword())) {
      setError("Enter your current password and a new one.");
      return;
    }
    if (newPassword() !== confirmedPassword()) {
      setError("The new passwords don't match.");
      return;
    }
    setStatus(FormStatus.SUBMITTING);
    const result = await changePassword({ currentPassword: currentPassword(), newPassword: newPassword() });
    setStatus(FormStatus.EDITING);
    setError(failureReason(result));
  };

  return (
    <Show when={currentUser()?.state === SessionState.PASSWORD_CHANGE_REQUIRED} fallback={<Navigate href={AppRoutes.HOME} />}>
      <div class="login">
        <form class="login-card" onSubmit={submit}>
          <h1 class="brand">sona</h1>
          <p class="hint">Choose a new password to continue.</p>
          <label>
            Current password
            <input
              ref={currentPasswordInput}
              type="password"
              value={currentPassword()}
              onInput={(e) => setCurrentPassword(e.currentTarget.value)}
              autocomplete="current-password"
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
          <FormError error={error()} />
          <button type="submit" class="primary" disabled={status() === FormStatus.SUBMITTING}>
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
