package org.sona.metadata.resolver;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sona.client.MusicBrainzClient;
import org.sona.config.MusicBrainzConfig;
import org.sona.config.properties.MusicBrainzProperties;
import org.sona.metadata.RawMetadata;
import org.sona.metadata.Tag;
import org.sona.metadata.TagValue;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.TrackToIngest;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sona.utils.IDGenerator.uuidv7;

class MusicBrainzResolverTest {

    // Serves the stubs in src/test/resources/wiremock.
    @RegisterExtension
    static final WireMockExtension musicBrainz = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().usingFilesUnderClasspath("wiremock"))
            .build();

    private final MusicBrainzResolver resolver = new MusicBrainzResolver(client(musicBrainz.baseUrl() + "/ws/2/"));

    @Test
    void resolveTaggedTrack() throws IOException, InterruptedException {
        final var metadata = metadata("A Whisper", "Coldplay");
        metadata.add(Tag.TRACK_NUMBER, new TagValue.Int(9));

        final var resolved = resolver.resolveTrack(trackToIngest("09 - A Whisper.flac"), metadata);

        final var coldplay = new ResolvedArtist(UUID.fromString("cc197bad-dc9c-440d-a5b5-d52ba2e14234"), "Coldplay");
        assertThat(resolved).contains(new ResolvedTrack(
                UUID.fromString("53616964-f51e-4ce0-a589-a5581f771197"),
                "A Whisper",
                coldplay,
                UUID.fromString("5b59d66b-8902-4350-b2e3-324037ef4cb0"),
                "A Rush of Blood to the Head",
                coldplay,
                UUID.fromString("120c786d-a3b2-3c19-b4ff-2b7b3b4435bf"),
                "A Rush of Blood to the Head"
        ));
    }

    @Test
    void returnEmptyWhenNothingMatches() throws IOException, InterruptedException {
        final var metadata = metadata("Zzyzx Qwerty Nonexistent Song", "Nobody Atall");

        assertThat(resolver.resolveTrack(trackToIngest("unknown.flac"), metadata)).isEmpty();
    }

    private static RawMetadata metadata(final String title, final String artist) {
        final var metadata = new RawMetadata(Duration.ofSeconds(10));
        metadata.add(Tag.TRACK_TITLE, new TagValue.Str(title));
        metadata.add(Tag.TRACK_ARTIST, new TagValue.Str(artist));
        return metadata;
    }

    private static MusicBrainzClient client(final String url) {
        final var config = new MusicBrainzConfig();
        final var mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        final var restClient = config.musicBrainzRestClient(config.uriBuilderFactory(new MusicBrainzProperties(url)), mapper);
        return new MusicBrainzClient(restClient);
    }

    private static TrackToIngest trackToIngest(final String fileName) {
        return new TrackToIngest(uuidv7(), uuidv7(), fileName, IngestState.PENDING);
    }
}
