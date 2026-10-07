package org.sona.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.sona.config.MusicBrainzConfig;
import org.sona.config.properties.MusicBrainzProperties;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Serves recorded MusicBrainz responses so tests never hit the live API.
 * <p>
 * A request for {@code /ws/2/<entity>?query=<q>} is answered with the resource
 * {@code fixtures/<entity>/<slug(q)>.json}, where the slug is the lowercased query with every run of
 * non-alphanumeric characters replaced by '-'. Missing fixtures return 404 and print the expected file name;
 * record one with:
 * <pre>
 * curl -G -A "Sona/0.1.0 ( jonathan@vusich.cloud )" https://musicbrainz.org/ws/2/recording \
 *   --data-urlencode 'query=...' --data-urlencode fmt=json
 * </pre>
 * Queries whose slug is {@code http-<status>} are answered with that status code.
 */
public final class MusicBrainzFixtureServer {

    private static final MusicBrainzFixtureServer INSTANCE = new MusicBrainzFixtureServer();

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern STATUS_SLUG = Pattern.compile("http-(\\d{3})");

    private final HttpServer server;
    private volatile String lastQuery;

    private MusicBrainzFixtureServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/ws/2/", this::handle);
        server.start();
    }

    public static MusicBrainzFixtureServer instance() {
        return INSTANCE;
    }

    public MusicBrainzProperties properties() {
        return new MusicBrainzProperties(
                "http://%s:%d/ws/2/".formatted(server.getAddress().getHostString(), server.getAddress().getPort()),
                Duration.ofMillis(1) // effectively no rate limit; Resilience4j rejects zero
        );
    }

    /**
     * A client wired like the application's, pointed at this server.
     */
    public MusicBrainzClient client() {
        final var config = new MusicBrainzConfig();
        final var mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        final var restClient = config.musicBrainzRestClient(config.uriBuilderFactory(properties()), mapper, properties());
        return new MusicBrainzClient(restClient);
    }

    /**
     * The decoded {@code query} parameter of the most recent request.
     */
    public String lastQuery() {
        return lastQuery;
    }

    public static String slug(final String query) {
        return NON_ALPHANUMERIC.matcher(query.toLowerCase(Locale.ROOT)).replaceAll("-")
                .replaceAll("^-+|-+$", "");
    }

    private void handle(final HttpExchange exchange) throws IOException {
        try (exchange) {
            final var entity = exchange.getRequestURI().getPath().substring("/ws/2/".length());
            final var query = queryParameter(exchange.getRequestURI().getRawQuery(), "query");
            lastQuery = query;

            final var slug = slug(query);
            final var status = STATUS_SLUG.matcher(slug);
            if (status.matches()) {
                respond(exchange, Integer.parseInt(status.group(1)), "{}".getBytes(StandardCharsets.UTF_8));
                return;
            }

            final var resource = "fixtures/%s/%s.json".formatted(entity, slug);
            try (final var fixture = MusicBrainzFixtureServer.class.getResourceAsStream(resource)) {
                if (fixture == null) {
                    System.err.println("Missing MusicBrainz fixture: src/test/resources/org/sona/client/" + resource);
                    respond(exchange, 404, ("No fixture " + resource).getBytes(StandardCharsets.UTF_8));
                    return;
                }
                respond(exchange, 200, fixture.readAllBytes());
            }
        }
    }

    private static void respond(final HttpExchange exchange, final int status, final byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private static String queryParameter(final String rawQuery, final String name) {
        return Arrays.stream(rawQuery.split("&"))
                .map(parameter -> parameter.split("=", 2))
                .filter(pair -> pair[0].equals(name) && pair.length == 2)
                .map(pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8))
                .findFirst()
                .orElse("");
    }
}
