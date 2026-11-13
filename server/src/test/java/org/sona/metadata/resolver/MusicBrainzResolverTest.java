package org.sona.metadata.resolver;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sona.samples.FlacFiles.flacFile;
import static org.sona.utils.IDGenerator.uuidv7;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.javax.sonaudio.exception.InvalidFormatException;
import dev.javax.sonaudio.fingerprint.Fingerprint;
import dev.javax.sonaudio.format.flac.FlacParser;
import dev.javax.sonaudio.metadata.RawMetadata;
import dev.javax.sonaudio.metadata.RawMetadataBuilder;
import dev.javax.sonaudio.metadata.Tag;
import dev.javax.sonaudio.metadata.TagValue;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sona.client.AcoustIdClient;
import org.sona.client.MusicBrainzClient;
import org.sona.config.AcoustIdConfig;
import org.sona.config.MusicBrainzConfig;
import org.sona.config.properties.AcoustIdProperties;
import org.sona.config.properties.MusicBrainzProperties;
import org.sona.model.enums.IngestState;
import org.sona.model.enums.ReleaseGroupType;
import org.sona.model.tables.pojos.TrackToIngest;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class MusicBrainzResolverTest {

    // Serves the stubs in src/test/resources/wiremock.
    @RegisterExtension
    static final WireMockExtension musicBrainz = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().usingFilesUnderClasspath("wiremock"))
            .build();

    private static final UUID COLDPLAY_ID = UUID.fromString("cc197bad-dc9c-440d-a5b5-d52ba2e14234");
    private static final UUID M83_ID = UUID.fromString("6d7b7cd4-254b-4c25-83f6-dd20f98ceacd");
    private static final UUID ALBUM_ID = UUID.fromString("a1b1c1d1-0000-4000-8000-000000000001");
    private static final UUID COMPILATION_ID = UUID.fromString("a1b1c1d1-0000-4000-8000-000000000002");
    private static final UUID SONG_ONE_ID = UUID.fromString("a1b1c1d1-0000-4000-8000-000000000011");
    private static final UUID SONG_TWO_ID = UUID.fromString("a1b1c1d1-0000-4000-8000-000000000012");
    private static final UUID BONUS_ID = UUID.fromString("a1b1c1d1-0000-4000-8000-000000000013");
    private static final ResolvedArtist BAND = new ResolvedArtist(COLDPLAY_ID, "Band", null);
    private static final String BAND_CREDIT =
            "{\"name\": \"Band\", \"artist\": {\"id\": \"" + COLDPLAY_ID + "\", \"name\": \"Band\"}}";

    private final MusicBrainzResolver resolver = new MusicBrainzResolver(
            newClient(musicBrainz.baseUrl() + "/ws/2/"), newAcoustIdClient(musicBrainz.baseUrl() + "/v2/"));

    @Test
    void resolveTaggedTrack() throws IOException, InvalidFormatException {
        final var metadata = numberedMetadata("A Whisper", "Coldplay");

        // AcoustID knows no recording with this fingerprint (the stub in src/test/resources/wiremock/mappings).
        final var resolved = resolver.resolveTrack(
                trackToIngest("09 - A Whisper.flac"), metadata, metadata.duration(), fingerprint());

        assertThat(resolved)
                .isEqualTo(new TrackResolution.Matched(new ResolvedTrack(
                        UUID.fromString("53616964-f51e-4ce0-a589-a5581f771197"),
                        "A Whisper",
                        new ResolvedArtist(COLDPLAY_ID, "Coldplay", null),
                        UUID.fromString("5b59d66b-8902-4350-b2e3-324037ef4cb0"),
                        1,
                        9)));
    }

    @Test
    void resolveTheRecordingAFingerprintMatchesCloselyWhateverTheTagsSay() throws IOException, InvalidFormatException {
        stubFingerprintMatch(aResponse().withBodyFile("acoustid/lower-your-eyelids-to-die-with-the-sun.json"));
        // Lower Your Eyelids to Die With the Sun is 10:37 long.
        final var metadata = numberedMetadata("A Whisper", "Coldplay", Duration.ofSeconds(638));

        final var resolved = resolver.resolveTrack(
                trackToIngest("09 - A Whisper.flac"), metadata, metadata.duration(), fingerprint());

        assertThat(resolved)
                .isEqualTo(new TrackResolution.Matched(new ResolvedTrack(
                        UUID.fromString("cd2e7c47-16f5-46c6-a37c-a1eb7bf599ff"),
                        "Lower Your Eyelids to Die With the Sun",
                        // Unlike a search, a lookup gives the artist's country.
                        new ResolvedArtist(M83_ID, "M83", "FR"),
                        UUID.fromString("db85c244-53e7-441c-bab0-52c9c0d27450"),
                        1,
                        15)));
    }

    @Test
    void preferTheTaggedRecordingALooseFingerprintMatchIsOf() throws IOException, InvalidFormatException {
        // The instrumental A Whisper, the second recording the tags find, matches loosely.
        stubFingerprintMatch(aResponse().withBody("""
                {"results": [{"id": "9ff43b6a-4f16-427c-93c2-92307ca505e0", "score": 0.6,
                    "recordings": [{"id": "41498c37-0a69-47a8-98d7-bdfab161559e"}]}], "status": "ok"}
                """));
        final var metadata = numberedMetadata("A Whisper", "Coldplay");

        final var resolved = resolver.resolveTrack(
                trackToIngest("09 - A Whisper.flac"), metadata, metadata.duration(), fingerprint());

        assertThat(matchedTrack(resolved))
                .extracting(ResolvedTrack::recordingId, ResolvedTrack::releaseId)
                .containsExactly(
                        UUID.fromString("41498c37-0a69-47a8-98d7-bdfab161559e"),
                        UUID.fromString("a5ab3079-99e6-4a13-9caa-a90291e39d88"));
    }

    @Test
    void resolveFromTheTagsWhenALooseFingerprintMatchIsOfNoTaggedRecording()
            throws IOException, InvalidFormatException {
        stubFingerprintMatch(aResponse().withBody("""
                {"results": [{"id": "9ff43b6a-4f16-427c-93c2-92307ca505e0", "score": 0.6,
                    "recordings": [{"id": "cd2e7c47-16f5-46c6-a37c-a1eb7bf599ff"}]}], "status": "ok"}
                """));
        final var metadata = numberedMetadata("A Whisper", "Coldplay");

        final var resolved = resolver.resolveTrack(
                trackToIngest("09 - A Whisper.flac"), metadata, metadata.duration(), fingerprint());

        assertThat(matchedTrack(resolved).recordingId())
                .isEqualTo(UUID.fromString("53616964-f51e-4ce0-a589-a5581f771197"));
    }

    @Test
    void resolveReleaseWithItsReleaseGroupAndArtists() {
        final var releaseId = UUID.fromString("5b59d66b-8902-4350-b2e3-324037ef4cb0");

        final var release = resolver.resolveRelease(releaseId);

        final var coldplay = new ResolvedArtist(COLDPLAY_ID, "Coldplay", "GB");
        assertThat(release)
                .isEqualTo(new ResolvedRelease(
                        releaseId,
                        "A Rush of Blood to the Head",
                        LocalDate.of(2022, 8, 26),
                        null,
                        List.of(new ResolvedLabel(
                                UUID.fromString("df7d1c7f-ef95-425f-8eef-445b3d7bcbd9"), "Parlophone")),
                        List.of(new ResolvedMedium(1, "Digital Media")),
                        coldplay,
                        new ResolvedReleaseGroup(
                                UUID.fromString("120c786d-a3b2-3c19-b4ff-2b7b3b4435bf"),
                                "A Rush of Blood to the Head",
                                ReleaseGroupType.ALBUM,
                                LocalDate.of(2002, 8, 12),
                                coldplay)));
    }

    @Test
    void resolveArtistWithTheirCountry() {
        assertThat(resolver.resolveArtist(COLDPLAY_ID)).isEqualTo(new ResolvedArtist(COLDPLAY_ID, "Coldplay", "GB"));
    }

    @Test
    void askForReviewWithNoCandidatesWhenNothingMatches() throws IOException, InvalidFormatException {
        final var metadata = newMetadata("Zzyzx Qwerty Nonexistent Song", "Nobody Atall");

        assertThat(resolver.resolveTrack(trackToIngest("unknown.flac"), metadata, metadata.duration(), fingerprint()))
                .isEqualTo(new TrackResolution.NeedsReview(List.of()));
    }

    @Test
    void acceptARecordingExactlyThreeSecondsFromTheAudio() throws IOException, InvalidFormatException {
        // A Whisper is 238.000 seconds long.
        final var metadata = numberedMetadata("A Whisper", "Coldplay", Duration.ofSeconds(241));

        final var resolved = resolver.resolveTrack(
                trackToIngest("09 - A Whisper.flac"), metadata, metadata.duration(), fingerprint());

        assertThat(matchedTrack(resolved).recordingId())
                .isEqualTo(UUID.fromString("53616964-f51e-4ce0-a589-a5581f771197"));
    }

    @Test
    void askForReviewWhenNoRecordingIsWithinThreeSecondsOfTheAudio() throws IOException, InvalidFormatException {
        // The three recordings are 238.000, 237.000 and 238.333 seconds long.
        final var metadata = numberedMetadata("A Whisper", "Coldplay", Duration.ofSeconds(242));

        final var resolved = resolver.resolveTrack(
                trackToIngest("09 - A Whisper.flac"), metadata, metadata.duration(), fingerprint());

        assertThat(resolved).isInstanceOf(TrackResolution.NeedsReview.class);
        final var candidates = ((TrackResolution.NeedsReview) resolved).candidates();
        assertThat(candidates).extracting(TrackCandidate::score).containsExactly(50, 50, 50);
    }

    @Test
    void ignoreAFingerprintMatchOfARecordingOfAnotherLength() throws IOException, InvalidFormatException {
        stubFingerprintMatch(aResponse().withBodyFile("acoustid/lower-your-eyelids-to-die-with-the-sun.json"));
        // 10:37 is far from A Whisper's length, but the audio is 4 minutes long, so the tags decide.
        final var metadata = numberedMetadata("A Whisper", "Coldplay");

        final var resolved = resolver.resolveTrack(
                trackToIngest("09 - A Whisper.flac"), metadata, metadata.duration(), fingerprint());

        assertThat(matchedTrack(resolved).recordingId())
                .isEqualTo(UUID.fromString("53616964-f51e-4ce0-a589-a5581f771197"));
    }

    @Test
    void chooseTheReleaseTheAlbumTagNames() throws IOException, InvalidFormatException {
        final var metadata = albumMetadata("a rush of blood to the head");

        final var resolved = resolver.resolveTrack(
                trackToIngest("09 - A Whisper.flac"), metadata, metadata.duration(), fingerprint());

        assertThat(matchedTrack(resolved).releaseId())
                .isEqualTo(UUID.fromString("5b59d66b-8902-4350-b2e3-324037ef4cb0"));
    }

    @Test
    void askForReviewWhenNoReleaseHasTheAlbumTagsTitle() throws IOException, InvalidFormatException {
        final var metadata = albumMetadata("Parachutes");

        final var resolved = resolver.resolveTrack(
                trackToIngest("09 - A Whisper.flac"), metadata, metadata.duration(), fingerprint());

        assertThat(resolved).isInstanceOf(TrackResolution.NeedsReview.class);
        final var candidates = ((TrackResolution.NeedsReview) resolved).candidates();
        // Each of the three recordings once, on its first release, all equally short of an album match.
        assertThat(candidates)
                .extracting(candidate -> candidate.track().recordingId(), TrackCandidate::score)
                .containsExactly(
                        tuple(UUID.fromString("53616964-f51e-4ce0-a589-a5581f771197"), 70),
                        tuple(UUID.fromString("41498c37-0a69-47a8-98d7-bdfab161559e"), 70),
                        tuple(UUID.fromString("551f064e-1682-4c94-b538-9407978a4f63"), 70));
    }

    @Test
    void matchAnAlbumOnTheReleaseWithAsManyTracksAsItHasFiles() throws IOException, InvalidFormatException {
        // The compilation has three tracks, so it isn't even looked up.
        stubSearch(
                "Song One",
                recordingJson(SONG_ONE_ID, "Song One", searchRelease(COMPILATION_ID, 3), searchRelease(ALBUM_ID, 2)));
        stubSearch("Song Two", recordingJson(SONG_TWO_ID, "Song Two", searchRelease(ALBUM_ID, 2)));
        stubRelease(
                ALBUM_ID, mediumJson(1, trackJson(1, SONG_TWO_ID, "Song Two"), trackJson(2, SONG_ONE_ID, "Song One")));

        final var resolved = resolver.resolveAlbum(List.of(albumTrack("Song One"), albumTrack("Song Two")));

        assertThat(resolved)
                .isEqualTo(new AlbumResolution.Matched(List.of(
                        new ResolvedTrack(SONG_ONE_ID, "Song One", BAND, ALBUM_ID, 1, 2),
                        new ResolvedTrack(SONG_TWO_ID, "Song Two", BAND, ALBUM_ID, 1, 1))));
    }

    @Test
    void matchAnAlbumOnOneMediumOfARelease() throws IOException, InvalidFormatException {
        stubSearch("Song One", recordingJson(SONG_ONE_ID, "Song One", searchRelease(ALBUM_ID, 4, 2, 2)));
        stubSearch("Song Two", recordingJson(SONG_TWO_ID, "Song Two", searchRelease(ALBUM_ID, 4, 2, 2)));
        stubRelease(
                ALBUM_ID,
                mediumJson(1, trackJson(1, BONUS_ID, "Bonus Song"), trackJson(2, UUID.randomUUID(), "Other Song")),
                mediumJson(2, trackJson(1, SONG_ONE_ID, "Song One"), trackJson(2, SONG_TWO_ID, "Song Two")));

        final var resolved = resolver.resolveAlbum(List.of(albumTrack("Song One"), albumTrack("Song Two")));

        assertThat(resolved)
                .isEqualTo(new AlbumResolution.Matched(List.of(
                        new ResolvedTrack(SONG_ONE_ID, "Song One", BAND, ALBUM_ID, 2, 1),
                        new ResolvedTrack(SONG_TWO_ID, "Song Two", BAND, ALBUM_ID, 2, 2))));
    }

    @Test
    void askForReviewOfAnAlbumNoReleasePlacesEveryFileOf() throws IOException, InvalidFormatException {
        stubSearch("Song One", recordingJson(SONG_ONE_ID, "Song One", searchRelease(ALBUM_ID, 3)));
        stubSearch("Song Two", recordingJson(SONG_TWO_ID, "Song Two", searchRelease(ALBUM_ID, 3)));
        stubSearch("Bonus Song", recordingJson(BONUS_ID, "Bonus Song", searchRelease(COMPILATION_ID, 9)));
        stubRelease(
                ALBUM_ID,
                mediumJson(
                        1,
                        trackJson(1, SONG_ONE_ID, "Song One"),
                        trackJson(2, SONG_TWO_ID, "Song Two"),
                        trackJson(3, UUID.randomUUID(), "Other Song")));

        final var resolved = resolver.resolveAlbum(
                List.of(albumTrack("Song One"), albumTrack("Song Two"), albumTrack("Bonus Song")));

        final var review = needsReview(resolved);
        assertThat(review.releases())
                .containsExactly(new ReleaseCandidate(
                        ALBUM_ID,
                        "Release " + ALBUM_ID,
                        BAND,
                        LocalDate.of(2002, 8, 26),
                        "GB",
                        new ReleaseScope.WholeRelease(),
                        2));
    }

    @Test
    void placeNoAlbumFileByALooseFingerprintMatchAlone() throws IOException, InvalidFormatException {
        final var otherSong = UUID.randomUUID();
        stubFingerprintMatch(aResponse().withBody("""
                {"results": [{"id": "9ff43b6a-4f16-427c-93c2-92307ca505e0", "score": 0.6,
                    "recordings": [{"id": "%s"}]}], "status": "ok"}
                """.formatted(otherSong)));
        stubSearch("Song One", recordingJson(SONG_ONE_ID, "Song One", searchRelease(ALBUM_ID, 1)));
        stubRelease(ALBUM_ID, mediumJson(1, trackJson(1, otherSong, "Other Song")));

        final var resolved = resolver.resolveAlbum(List.of(albumTrack("Song One")));

        assertThat(needsReview(resolved).releases())
                .extracting(ReleaseCandidate::placedCount)
                .containsExactly(0);
    }

    @Test
    void askForReviewOfAnAlbumWhoseTracksAreNotAsLongAsItsFiles() throws IOException, InvalidFormatException {
        stubSearch("Song One", recordingJson(SONG_ONE_ID, "Song One", searchRelease(ALBUM_ID, 1)));
        stubRelease(ALBUM_ID, mediumJson(1, trackJson(1, SONG_ONE_ID, "Song One", Duration.ofMinutes(5))));

        final var resolved = resolver.resolveAlbum(List.of(albumTrack("Song One")));

        assertThat(needsReview(resolved).releases())
                .extracting(ReleaseCandidate::placedCount)
                .containsExactly(1);
    }

    @Test
    void askForReviewWithNoReleasesWhenNoneHasAsManyTracksAsTheAlbumHasFiles()
            throws IOException, InvalidFormatException {
        // Nor is the release looked up.
        stubSearch("Song One", recordingJson(SONG_ONE_ID, "Song One", searchRelease(ALBUM_ID, 11)));

        final var resolved = resolver.resolveAlbum(List.of(albumTrack("Song One")));

        assertThat(needsReview(resolved).releases()).isEmpty();
    }

    private static AlbumResolution.NeedsReview needsReview(final AlbumResolution resolution) {
        return switch (resolution) {
            case AlbumResolution.NeedsReview review -> review;
            case AlbumResolution.Matched _ -> throw new AssertionError("Expected a review, but " + resolution);
        };
    }

    private static AlbumTrack albumTrack(final String title) throws IOException, InvalidFormatException {
        final var metadata = newMetadata(title, "Band");
        return new AlbumTrack(trackToIngest(title + ".flac"), metadata, Duration.ofSeconds(240), fingerprint());
    }

    private static void stubSearch(final String title, final String recordingJson) {
        musicBrainz.stubFor(get(urlPathEqualTo("/ws/2/recording"))
                .withQueryParam("query", containing("\"" + title + "\""))
                .atPriority(1)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"recordings\": [" + recordingJson + "]}")));
    }

    // A 4:00 recording of the band, on each release in turn, found by the search with full marks.
    private static String recordingJson(final UUID recordingId, final String title, final String... releases) {
        return """
                {"id": "%s", "score": 100, "title": "%s", "length": 240000, "artist-credit": [%s],
                 "releases": [%s]}""".formatted(recordingId, title, BAND_CREDIT, String.join(",", releases));
    }

    // A release of one medium, as a recording search lists it.
    private static String searchRelease(final UUID releaseId, final int trackCount) {
        return searchRelease(releaseId, trackCount, 1, trackCount);
    }

    // A recording search lists only the medium the recording is on.
    private static String searchRelease(
            final UUID releaseId, final int trackCount, final int mediumPosition, final int mediumTrackCount) {
        return """
                {"id": "%s", "title": "Release %s", "track-count": %d,
                 "media": [{"position": %d, "track-count": %d, "track-offset": 0}]}""".formatted(releaseId, releaseId, trackCount, mediumPosition, mediumTrackCount);
    }

    // The release, looked up with its tracklist.
    private static void stubRelease(final UUID releaseId, final String... media) {
        musicBrainz.stubFor(get(urlPathEqualTo("/ws/2/release/" + releaseId))
                .atPriority(1)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id": "%s", "title": "Release %s", "date": "2002-08-26", "country": "GB",
                                 "artist-credit": [%s], "media": [%s]}""".formatted(releaseId, releaseId, BAND_CREDIT, String.join(",", media)))));
    }

    private static String mediumJson(final int position, final String... tracks) {
        return """
                {"position": %d, "track-count": %d, "tracks": [%s]}""".formatted(position, tracks.length, String.join(",", tracks));
    }

    private static String trackJson(final int position, final UUID recordingId, final String title) {
        return trackJson(position, recordingId, title, Duration.ofSeconds(240));
    }

    private static String trackJson(
            final int position, final UUID recordingId, final String title, final Duration length) {
        final var millis = length.toMillis();
        return """
                {"id": "%s", "position": %d, "number": "%d", "title": "%s", "length": %d,
                 "recording": {"id": "%s", "title": "%s", "length": %d}, "artist-credit": [%s]}""".formatted(
                        UUID.randomUUID(), position, position, title, millis, recordingId, title, millis, BAND_CREDIT);
    }

    private static ResolvedTrack matchedTrack(final TrackResolution resolution) {
        return switch (resolution) {
            case TrackResolution.Matched matched -> matched.track();
            case TrackResolution.NeedsReview _ -> throw new AssertionError("Expected a match, but " + resolution);
        };
    }

    // Above the stub in src/test/resources/wiremock/mappings that matches every fingerprint with nothing.
    private static void stubFingerprintMatch(final ResponseDefinitionBuilder response) {
        musicBrainz.stubFor(post(urlPathEqualTo("/v2/lookup"))
                .atPriority(1)
                .willReturn(response.withHeader("Content-Type", "application/json")));
    }

    // Of the silence every test FLAC file has as its audio.
    private static Fingerprint fingerprint() throws IOException, InvalidFormatException {
        final var flac = new SeekableInMemoryByteChannel(flacFile(newMetadata("Silence", "Nobody")));
        return new Fingerprint(new FlacParser().parse(flac).audio());
    }

    private static RawMetadata numberedMetadata(final String title, final String artist) {
        // A Whisper is 3:58 long.
        return numberedMetadata(title, artist, Duration.ofSeconds(240));
    }

    private static RawMetadata numberedMetadata(final String title, final String artist, final Duration duration) {
        final var metadata = new RawMetadataBuilder(duration);
        metadata.addTag(Tag.TRACK_TITLE, new TagValue.Str(title));
        metadata.addTag(Tag.TRACK_ARTIST, new TagValue.Str(artist));
        metadata.addTag(Tag.TRACK_NUMBER, new TagValue.Int(9));
        return metadata.build();
    }

    private static RawMetadata albumMetadata(final String album) {
        final var metadata = new RawMetadataBuilder(Duration.ofSeconds(240));
        metadata.addTag(Tag.TRACK_TITLE, new TagValue.Str("A Whisper"));
        metadata.addTag(Tag.TRACK_ARTIST, new TagValue.Str("Coldplay"));
        metadata.addTag(Tag.TRACK_NUMBER, new TagValue.Int(9));
        metadata.addTag(Tag.RELEASE_TITLE, new TagValue.Str(album));
        return metadata.build();
    }

    private static RawMetadata newMetadata(final String title, final String artist) {
        final var metadata = new RawMetadataBuilder(Duration.ofSeconds(10));
        metadata.addTag(Tag.TRACK_TITLE, new TagValue.Str(title));
        metadata.addTag(Tag.TRACK_ARTIST, new TagValue.Str(artist));
        return metadata.build();
    }

    private static MusicBrainzClient newClient(final String url) {
        final var config = new MusicBrainzConfig();
        final var mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        final var restClient =
                config.musicBrainzRestClient(config.uriBuilderFactory(new MusicBrainzProperties(url)), mapper);
        return new MusicBrainzClient(restClient);
    }

    private static AcoustIdClient newAcoustIdClient(final String url) {
        final var properties = new AcoustIdProperties(url);
        final var mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        return new AcoustIdClient(new AcoustIdConfig().acoustIdRestClient(properties, mapper));
    }

    private static TrackToIngest trackToIngest(final String fileName) {
        return new TrackToIngest(uuidv7(), uuidv7(), fileName, IngestState.UPLOADED);
    }
}
