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

    public static final String CURRENT_USER = "/api/users/me";
    public static final String CURRENT_USER_PASSWORD = CURRENT_USER + "/password";

    public static final String JWKS = "/.well-known/jwks.json";
}
