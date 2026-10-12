// Calls to Sona's HTTP API. The frontend is served from the same origin, so the refresh cookie travels with auth calls.

/** The codes Sona refuses requests with (ErrorCode on the server). They never change once released. */
export const ErrorCode = {
  NOT_SIGNED_IN: 100,
  INVALID_CREDENTIALS: 101,
  INVALID_REFRESH_TOKEN: 102,
  PASSWORD_CHANGE_REQUIRED: 103,
  ACCESS_DENIED: 104,
  ACCOUNT_DISABLED: 105,
  INCORRECT_CURRENT_PASSWORD: 200,
  INVALID_NEW_PASSWORD: 201,
  PASSWORD_MANAGED_BY_PROVIDER: 202,
} as const;

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: number | null,
    reason: string,
  ) {
    super(reason);
  }
}

/**
 * Sends a request to Sona and returns its JSON body, or undefined when there is none.
 *
 * @throws ApiError when Sona refuses the request or can't be reached
 */
export async function send<T>(method: string, path: string, options: { token?: string; body?: unknown } = {}): Promise<T> {
  const headers: Record<string, string> = {};
  if (options.token !== undefined) {
    headers.Authorization = `Bearer ${options.token}`;
  }
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  let response: Response;
  try {
    response = await fetch(path, {
      method,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });
  } catch {
    throw new ApiError(0, null, "Can't reach Sona. Check your connection and try again.");
  }
  if (!response.ok) {
    throw await errorOf(response);
  }
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T);
}

async function errorOf(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as { code: number; reason: string };
    return new ApiError(response.status, body.code, body.reason);
  } catch {
    return new ApiError(response.status, null, "Something went wrong. Try again.");
  }
}
