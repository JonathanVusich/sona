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

    /**
     * The signed-in user's own playlists.
     */
    public static final String PLAYLISTS = API + "/playlists";

    public static final String PLAYLIST = PLAYLISTS + "/{playlistId}";
    public static final String PLAYLIST_TRACKS = PLAYLIST + "/tracks";
    public static final String PLAYLIST_TRACK = PLAYLIST_TRACKS + "/{trackId}";
    /**
     * Where the track is on the playlist, which setting moves it.
     */
    public static final String PLAYLIST_TRACK_POSITION = PLAYLIST_TRACK + "/position";

    /**
     * The signed-in user's own play history.
     */
    public static final String PLAYS = API + "/plays";

    /**
     * The signed-in user's own play queue.
     */
    public static final String QUEUE = API + "/queue";
    /**
     * The queue's entries in the order they play, a page at a time, from the cursor on.
     */
    public static final String QUEUE_ENTRIES = QUEUE + "/entries";
    /**
     * The entries that play before the cursor, a page at a time, going back.
     */
    public static final String QUEUE_EARLIER_ENTRIES = QUEUE_ENTRIES + "/earlier";
    /**
     * Followed by a playlist's ID, this is where a PUT fills the queue with the playlist's tracks.
     */
    public static final String QUEUE_PLAYLIST = QUEUE + "/playlists/{playlistId}";
    /**
     * Followed by a release's ID, this is where a PUT fills the queue with the release's tracks.
     */
    public static final String QUEUE_RELEASE = QUEUE + "/releases/{releaseId}";
    /**
     * Which entry of the queue the player is on, which setting moves it.
     */
    public static final String QUEUE_CURRENT = QUEUE + "/current";
    /**
     * How far into the current entry the player is.
     */
    public static final String QUEUE_POSITION = QUEUE + "/position";
    /**
     * The queue's shuffle and repeat modes.
     */
    public static final String QUEUE_MODES = QUEUE + "/modes";

    public static final String INGEST_GROUPS = API + "/ingest-groups";
    /**
     * Under the ID Sona gave the group when the uploader created it.
     */
    public static final String INGEST_GROUP = INGEST_GROUPS + "/{groupIngestId}";

    public static final String INGEST_GROUP_TRACKS = INGEST_GROUP + "/tracks";
    /**
     * Under the ID Sona gave the file when the group was created, which the file is uploaded to.
     */
    public static final String INGEST_GROUP_TRACK = INGEST_GROUP_TRACKS + "/{trackIngestId}";

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
