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

    public static final String RELEASES = API + "/releases";
    public static final String RELEASE = RELEASES + "/{releaseId}";
    public static final String RELEASE_GROUPS = API + "/release-groups";
    public static final String RELEASE_GROUP = RELEASE_GROUPS + "/{releaseGroupId}";
    public static final String ARTISTS = API + "/artists";
    public static final String ARTIST = ARTISTS + "/{artistId}";

    public static final String INGEST_GROUPS = API + "/ingest-groups";
    /**
     * Under the ID Sona gave the group when the uploader created it.
     */
    public static final String INGEST_GROUP = INGEST_GROUPS + "/{groupIngestId}";

    public static final String INGEST_GROUP_TRACKS = INGEST_GROUP + "/tracks";

    public static final String JWKS = "/.well-known/jwks.json";

    /**
     * Where clients open a STOMP connection over WebSocket, for what Sona pushes to them. Browsers can't set headers on
     * the handshake, so the access token goes in the CONNECT frame's {@code Authorization} header instead.
     */
    public static final String SOCKET = API + "/socket";
    /**
     * Destinations under here are the broker's: subscribers get every message sent to them.
     */
    public static final String SOCKET_TOPICS = "/topic";
    /**
     * Destinations under here are answered by Sona, once, to the one subscriber.
     */
    public static final String SOCKET_APP = "/app";

    /**
     * Each ingest's new state, whenever it changes.
     */
    public static final String INGEST_PROGRESS = SOCKET_TOPICS + "/ingests";
    /**
     * The {@code @SubscribeMapping} of {@link #UNFINISHED_INGESTS}, which is under {@link #SOCKET_APP}.
     */
    public static final String UNFINISHED_INGESTS_MAPPING = "/ingests";
    /**
     * Answers with every ingest that hasn't completed, so a client can subscribe here after {@link #INGEST_PROGRESS}
     * to catch up on what it missed.
     */
    public static final String UNFINISHED_INGESTS = SOCKET_APP + UNFINISHED_INGESTS_MAPPING;
}
