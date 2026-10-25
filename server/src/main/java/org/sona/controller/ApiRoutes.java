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
    public static final String TRACKS = API + "/tracks";
    public static final String TRACK = TRACKS + "/{trackId}";
    /**
     * Gives a token that may stream the track, as a URL a client can hand an audio element.
     */
    public static final String TRACK_STREAM_TOKEN = TRACK + "/stream-token";
    /**
     * The track's file as it's stored, with its stream token as the {@code access_token} parameter. Takes range
     * requests, so players can seek.
     */
    public static final String TRACK_STREAM = TRACK + "/stream";

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
     * Destinations under here are the broker's. Each user has their own copy of one, which they subscribe to under
     * {@link #SOCKET_USER}.
     */
    public static final String SOCKET_QUEUES = "/queue";
    /**
     * A client subscribes to its user's own copy of a queue under here, as {@code /user/queue/…}.
     */
    public static final String SOCKET_USER = "/user";

    /**
     * Where Sona sends an uploader each new state of their ingests.
     */
    public static final String INGEST_PROGRESS_QUEUE = SOCKET_QUEUES + "/ingests";
    /**
     * Where an uploader subscribes to {@link #INGEST_PROGRESS_QUEUE}, for the ingests of the groups they created.
     */
    public static final String INGEST_PROGRESS = SOCKET_USER + INGEST_PROGRESS_QUEUE;
}
