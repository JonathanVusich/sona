// Response bodies, as the server's controller.response records define them.

export const UserRole = {
  ADMIN: "ADMIN",
  USER: "USER",
} as const;

export type UserRole = (typeof UserRole)[keyof typeof UserRole];

export const Permission = {
  LIBRARY_READ: "LIBRARY_READ",
  LIBRARY_WRITE: "LIBRARY_WRITE",
  LIBRARY_UPLOAD: "LIBRARY_UPLOAD",
  USERS_MANAGE: "USERS_MANAGE",
} as const;

export type Permission = (typeof Permission)[keyof typeof Permission];

export const SessionState = {
  ACTIVE: "ACTIVE",
  // Until they change their password, the user may only do that.
  PASSWORD_CHANGE_REQUIRED: "PASSWORD_CHANGE_REQUIRED",
} as const;

export type SessionState = (typeof SessionState)[keyof typeof SessionState];

export const TokenType = {
  BEARER: "BEARER",
} as const;

export type TokenType = (typeof TokenType)[keyof typeof TokenType];

export interface TokenResponse {
  accessToken: string;
  tokenType: TokenType;
  /** Seconds until the access token expires. */
  expiresIn: number;
}

export interface CurrentUserResponse {
  userId: string;
  username: string;
  role: UserRole;
  state: SessionState;
  /** What the user may do now, which is nothing while they must change their password. */
  permissions: Permission[];
}

/** Why Sona refused a request. Clients branch on the number, which never changes once released. */
export const ErrorCode = {
  NOT_SIGNED_IN: 100,
  INVALID_CREDENTIALS: 101,
  INVALID_REFRESH_TOKEN: 102,
  PASSWORD_CHANGE_REQUIRED: 103,
  ACCESS_DENIED: 104,
  ACCOUNT_DISABLED: 105,
  SIGN_IN_FAILED: 106,
  INVALID_REGISTRATION_TOKEN: 107,
  SIGN_IN_REQUIRED: 108,

  INCORRECT_CURRENT_PASSWORD: 200,
  INVALID_NEW_PASSWORD: 201,
  PASSWORD_MANAGED_BY_PROVIDER: 202,
  USERNAME_TAKEN: 203,
  INVALID_USERNAME: 204,
  ALREADY_REGISTERED: 205,
} as const;

export type ErrorCode = (typeof ErrorCode)[keyof typeof ErrorCode];

/** The body of every client error Sona sends. */
export interface ErrorResponse {
  code: ErrorCode;
  /** What went wrong, for people. */
  reason: string;
}
