package org.sona.controller;

import lombok.experimental.UtilityClass;

/**
 * The paths of Sona's HTTP endpoints.
 */
@UtilityClass
public final class ApiRoutes {

    public static final String AUTH = "/api/auth";
    public static final String AUTH_TOKEN = AUTH + "/token";
    public static final String AUTH_REFRESH = AUTH + "/refresh";
    public static final String AUTH_LOGOUT = AUTH + "/logout";

    public static final String OIDC = AUTH + "/oidc";
    public static final String OIDC_PROVIDERS = OIDC + "/providers";
    /**
     * Followed by the provider's ID, this starts signing in through it.
     */
    public static final String OIDC_AUTHORIZE = OIDC + "/authorize";
    /**
     * Followed by the provider's ID, this is where the provider sends the browser back.
     */
    public static final String OIDC_CALLBACK = OIDC + "/callback";

    public static final String CURRENT_USER = "/api/users/me";
    public static final String CURRENT_USER_PASSWORD = CURRENT_USER + "/password";

    public static final String JWKS = "/.well-known/jwks.json";
}
