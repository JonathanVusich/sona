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
import org.sona.model.enums.ReleaseGroupType;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.TrackToIngest;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
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

    private static final UUID COLDPLAY_ID = UUID.fromString("cc197bad-dc9c-440d-a5b5-d52ba2e14234");

    private final MusicBrainzResolver resolver = new MusicBrainzResolver(newClient(musicBrainz.baseUrl() + "/ws/2/"));

    @Test
    void resolveTaggedTrack() throws IOException, InterruptedException {
        final var metadata = newMetadata("A Whisper", "Coldplay");
        metadata.addTag(Tag.TRACK_NUMBER, new TagValue.Int(9));

        final var resolved = resolver.resolveTrack(trackToIngest("09 - A Whisper.flac"), metadata);

        assertThat(resolved).contains(new ResolvedTrack(
                UUID.fromString("53616964-f51e-4ce0-a589-a5581f771197"),
                "A Whisper",
                new ResolvedArtist(COLDPLAY_ID, "Coldplay", null),
                UUID.fromString("5b59d66b-8902-4350-b2e3-324037ef4cb0"),
                1,
                9
        ));
    }

    @Test
    void resolveReleaseWithItsReleaseGroupAndArtists() {
        final var releaseId = UUID.fromString("5b59d66b-8902-4350-b2e3-324037ef4cb0");

        final var release = resolver.resolveRelease(releaseId);

        final var coldplay = new ResolvedArtist(COLDPLAY_ID, "Coldplay", "GB");
        assertThat(release).isEqualTo(new ResolvedRelease(
                releaseId,
                "A Rush of Blood to the Head",
                LocalDate.of(2022, 8, 26),
                null,
                List.of(new ResolvedLabel(UUID.fromString("df7d1c7f-ef95-425f-8eef-445b3d7bcbd9"), "Parlophone")),
                List.of(new ResolvedMedium(1, "Digital Media")),
                coldplay,
                new ResolvedReleaseGroup(UUID.fromString("120c786d-a3b2-3c19-b4ff-2b7b3b4435bf"),
                        "A Rush of Blood to the Head", ReleaseGroupType.ALBUM, LocalDate.of(2002, 8, 12), coldplay)
        ));
    }

    @Test
    void resolveArtistWithTheirCountry() {
        assertThat(resolver.resolveArtist(COLDPLAY_ID)).isEqualTo(new ResolvedArtist(COLDPLAY_ID, "Coldplay", "GB"));
    }

    @Test
    void returnEmptyWhenNothingMatches() throws IOException, InterruptedException {
        final var metadata = newMetadata("Zzyzx Qwerty Nonexistent Song", "Nobody Atall");

        assertThat(resolver.resolveTrack(trackToIngest("unknown.flac"), metadata)).isEmpty();
    }

    private static RawMetadata newMetadata(final String title, final String artist) {
        final var metadata = new RawMetadata(Duration.ofSeconds(10));
        metadata.addTag(Tag.TRACK_TITLE, new TagValue.Str(title));
        metadata.addTag(Tag.TRACK_ARTIST, new TagValue.Str(artist));
        return metadata;
    }

    private static MusicBrainzClient newClient(final String url) {
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
