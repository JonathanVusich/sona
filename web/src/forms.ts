import type { ApiResult } from "./api/client";

// The state of a form that sends one request to Sona.

export const FormStatus = {
  EDITING: "editing",
  SUBMITTING: "submitting",
  FAILED: "failed",
} as const;

export type FormStatus = (typeof FormStatus)[keyof typeof FormStatus];

export type FormState =
  | { status: typeof FormStatus.EDITING }
  | { status: typeof FormStatus.SUBMITTING }
  | { status: typeof FormStatus.FAILED; reason: string };

export const EDITING: FormState = { status: FormStatus.EDITING };
export const SUBMITTING: FormState = { status: FormStatus.SUBMITTING };

/**
 * @returns the form as it is once Sona has answered: editing again, or failed with Sona's reason
 */
export function submittedForm(result: ApiResult<void>): FormState {
  if (result.ok) {
    return EDITING;
  }
  return failedForm(result.failure.reason);
}

export function failedForm(reason: string): FormState {
  return { status: FormStatus.FAILED, reason };
}
