package org.sona.controller;

import lombok.experimental.UtilityClass;

/**
 * The paths of Sona's HTTP endpoints.
 */
@UtilityClass
public final class ApiRoutes {

    public static final String CURRENT_USER = "/api/users/me";

    public static final String JWKS = "/.well-known/jwks.json";
}
