package org.sona.controller;

import lombok.experimental.UtilityClass;

/**
 * The paths of Sona's HTTP endpoints.
 */
@UtilityClass
public final class ApiRoutes {

    /**
     * Every endpoint is under here; every other path is the web frontend's.
     */
    public static final String API = "/api";

    public static final String AUTH = API + "/auth";
    public static final String AUTH_TOKEN = AUTH + "/token";
    public static final String AUTH_REFRESH = AUTH + "/refresh";
    public static final String AUTH_LOGOUT = AUTH + "/logout";
    public static final String AUTH_REGISTER = AUTH + "/register";

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

    public static final String CURRENT_USER = API + "/users/me";
    public static final String CURRENT_USER_PASSWORD = CURRENT_USER + "/password";

    public static final String JWKS = "/.well-known/jwks.json";
}
