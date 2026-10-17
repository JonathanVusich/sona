import { ResultKind, type ApiResult } from "./api/client";

// Whether a form is waiting for Sona to answer the request it sent.
export const FormStatus = {
  EDITING: "editing",
  SUBMITTING: "submitting",
} as const;

export type FormStatus = (typeof FormStatus)[keyof typeof FormStatus];

/**
 * @returns why Sona refused the form's request, or null when it succeeded
 */
export function failureReason(result: ApiResult<void>): string | null {
  if (result.kind === ResultKind.OK) {
    return null;
  }
  return result.reason;
}
