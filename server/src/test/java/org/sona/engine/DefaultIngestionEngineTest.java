package org.sona.engine;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.sona.model.Tables.ARTIST;
import static org.sona.model.Tables.LABEL;
import static org.sona.model.Tables.MEDIUM;
import static org.sona.model.Tables.RELEASE;
import static org.sona.model.Tables.RELEASE_GROUP;
import static org.sona.model.Tables.RELEASE_LABEL;
import static org.sona.samples.FlacFiles.flacFile;
import static org.sona.utils.IDGenerator.uuidv7;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.config.properties.LibraryProperties;
import org.sona.db.ArtistDao;
import org.sona.db.IngestDao;
import org.sona.db.ReleaseDao;
import org.sona.db.ReleaseGroupDao;
import org.sona.db.TrackDao;
import org.sona.db.UserDao;
import org.sona.exception.InvalidFormatException;
import org.sona.format.flac.FlacWriter;
import org.sona.library.Library;
import org.sona.metadata.RawMetadata;
import org.sona.metadata.Tag;
import org.sona.metadata.TagValue;
import org.sona.model.enums.AccountState;
import org.sona.model.enums.AudioFormat;
import org.sona.model.enums.IngestGroupKind;
import org.sona.model.enums.IngestState;
import org.sona.model.enums.ReleaseGroupType;
import org.sona.model.enums.UserRole;
import org.sona.model.enums.UserType;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.IngestGroup;
import org.sona.model.tables.pojos.Medium;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.pojos.TrackToIngest;
import org.sona.model.tables.pojos.Users;
import org.sona.samples.Mp3Sample;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class DefaultIngestionEngineTest {

    @Autowired
    private IngestionEngine engine;

    @Autowired
    private Library library;

    @Autowired
    private LibraryProperties libraryProperties;

    @Autowired
    private IngestDao ingestDao;

    @Autowired
    private UserDao userDao;

    @Autowired
    private TrackDao trackDao;

    @Autowired
    private ReleaseGroupDao releaseGroupDao;

    @Autowired
    private ReleaseDao releaseDao;

    @Autowired
    private ArtistDao artistDao;

    @Autowired
    private WireMockServer musicBrainz;
    // For rows the DAOs have no lookup for.
    @Autowired
    private DSLContext dsl;

    @Test
    void importMatchedTrack() throws IOException, InvalidFormatException, InterruptedException {
        final var flac = flacFile(whisperMetadata());
        // Upper-case extension on purpose: extension matching is case-insensitive.
        final var ingest = storeIngest(flac, "09 A Whisper.FLAC");

        engine.processTrack(ingest);

        assertThat(reloadIngest(ingest).state()).isEqualTo(IngestState.COMPLETED);

        final var track = loadTrack(ingest);
        assertThat(track.name()).isEqualTo("A Whisper");
        assertThat(track.duration()).isEqualTo(10);
        assertThat(track.format()).isEqualTo(AudioFormat.FLAC);
        assertThat(track.position()).isEqualTo(9);

        final var medium = dsl.selectFrom(MEDIUM)
                .where(MEDIUM.MEDIUM_ID.eq(track.mediumId()))
                .fetchSingleInto(Medium.class);
        assertThat(medium.position()).isEqualTo(1);
        final var release = dsl.selectFrom(RELEASE)
                .where(RELEASE.RELEASE_ID.eq(medium.releaseId()))
                .fetchSingleInto(Release.class);
        assertThat(release.musicbrainzReleaseId()).isEqualTo(UUID.fromString("5b59d66b-8902-4350-b2e3-324037ef4cb0"));
        assertThat(release.date()).isEqualTo(LocalDate.of(2022, 8, 26));
        final var labelNames = dsl.select(LABEL.NAME)
                .from(RELEASE_LABEL)
                .join(LABEL)
                .on(LABEL.LABEL_ID.eq(RELEASE_LABEL.LABEL_ID))
                .where(RELEASE_LABEL.RELEASE_ID.eq(release.releaseId()))
                .fetch(LABEL.NAME);
        assertThat(labelNames).containsExactly("Parlophone");
        final var media = dsl.selectFrom(MEDIUM)
                .where(MEDIUM.RELEASE_ID.eq(release.releaseId()))
                .fetchInto(Medium.class);
        assertThat(media).extracting(Medium::position, Medium::format).containsExactly(tuple(1, "Digital Media"));
        final var releaseGroup = dsl.selectFrom(RELEASE_GROUP)
                .where(RELEASE_GROUP.RELEASE_GROUP_ID.eq(release.releaseGroupId()))
                .fetchSingleInto(ReleaseGroup.class);
        assertThat(releaseGroup.musicbrainzReleaseGroupId())
                .isEqualTo(UUID.fromString("120c786d-a3b2-3c19-b4ff-2b7b3b4435bf"));
        assertThat(releaseGroup.type()).isEqualTo(ReleaseGroupType.ALBUM);
        assertThat(releaseGroup.firstReleaseDate()).isEqualTo(LocalDate.of(2002, 8, 12));

        final var artist = dsl.selectFrom(ARTIST)
                .where(ARTIST.ARTIST_ID.eq(track.artistId()))
                .fetchSingleInto(Artist.class);
        assertThat(artist.name()).isEqualTo("Coldplay");
        assertThat(artist.musicbrainzArtistId()).isEqualTo(UUID.fromString("cc197bad-dc9c-440d-a5b5-d52ba2e14234"));
        assertThat(artist.country()).isEqualTo("GB");
        assertThat(release.artistId()).isEqualTo(artist.artistId());
        assertThat(releaseGroup.artistId()).isEqualTo(artist.artistId());

        assertThat(track.path())
                .isEqualTo("%s[A Rush of Blood to the Head]/%s[A Rush of Blood to the Head]/%s[A Whisper].flac"
                        .formatted(releaseGroup.releaseGroupId(), release.releaseId(), track.trackId()));

        try (final var imported = library.trackFile(track).getInputStream()) {
            assertThat(imported.readAllBytes()).isEqualTo(flac);
        }
        assertThatThrownBy(() -> library.readIngestTrack(ingest)).isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void importMatchedMp3() throws IOException, InvalidFormatException, InterruptedException {
        final byte[] mp3;
        try (final var inputStream = Mp3Sample.STEREO_CBR_ID3V24.inputStream()) {
            mp3 = inputStream.readAllBytes();
        }
        final var ingest = storeIngest(mp3, "09 A Whisper.mp3");

        engine.processTrack(ingest);

        assertThat(reloadIngest(ingest).state()).isEqualTo(IngestState.COMPLETED);
        final var track = loadTrack(ingest);
        assertThat(track.name()).isEqualTo("A Whisper");
        assertThat(track.duration()).isEqualTo(1);
        assertThat(track.format()).isEqualTo(AudioFormat.MP3);
        assertThat(track.path()).endsWith(".mp3");
        try (final var imported = library.trackFile(track).getInputStream()) {
            assertThat(imported.readAllBytes()).isEqualTo(mp3);
        }
    }

    @Test
    void importTracksFromOneReleaseIntoOneFolder() throws IOException, InvalidFormatException, InterruptedException {
        final var first = storeIngest(flacFile(whisperMetadata()), "09 A Whisper.flac");
        final var second = storeIngest(flacFile(whisperMetadata()), "09 A Whisper (copy).flac");
        // The stub server is shared with other tests.
        musicBrainz.resetRequests();
        engine.processTrack(first);
        engine.processTrack(second);

        assertThat(loadTrack(second).mediumId()).isEqualTo(loadTrack(first).mediumId());
        assertThat(dsl.fetchCount(RELEASE)).isEqualTo(1);
        assertThat(dsl.fetchCount(RELEASE_GROUP)).isEqualTo(1);
        // The release was stored with the first track, so it isn't looked up again.
        assertThat(musicBrainz.findAll(getRequestedFor(urlPathMatching("/ws/2/release/.*"))))
                .hasSize(1);
    }

    @Test
    void finishStoringAReleaseStoredWithoutItsLabelsAndMedia()
            throws IOException, InvalidFormatException, InterruptedException {
        final var artist = artistDao.upsert(new Artist(
                uuidv7(), "Coldplay", "GB", null, 0, UUID.fromString("cc197bad-dc9c-440d-a5b5-d52ba2e14234")));
        final var releaseGroup = releaseGroupDao.upsert(new ReleaseGroup(
                uuidv7(),
                "A Rush of Blood to the Head",
                null,
                null,
                artist.artistId(),
                0,
                UUID.fromString("120c786d-a3b2-3c19-b4ff-2b7b3b4435bf")));
        final var release = releaseDao.upsert(new Release(
                uuidv7(),
                "A Rush of Blood to the Head",
                null,
                null,
                null,
                0,
                releaseGroup.releaseGroupId(),
                artist.artistId(),
                UUID.fromString("5b59d66b-8902-4350-b2e3-324037ef4cb0")));
        final var ingest = storeIngest(flacFile(whisperMetadata()), "09 A Whisper.flac");

        engine.processTrack(ingest);

        final var medium = dsl.selectFrom(MEDIUM)
                .where(MEDIUM.MEDIUM_ID.eq(loadTrack(ingest).mediumId()))
                .fetchSingleInto(Medium.class);
        assertThat(medium)
                .extracting(Medium::releaseId, Medium::position, Medium::format)
                .containsExactly(release.releaseId(), 1, "Digital Media");
        final var labelNames = dsl.select(LABEL.NAME)
                .from(RELEASE_LABEL)
                .join(LABEL)
                .on(LABEL.LABEL_ID.eq(RELEASE_LABEL.LABEL_ID))
                .where(RELEASE_LABEL.RELEASE_ID.eq(release.releaseId()))
                .fetch(LABEL.NAME);
        assertThat(labelNames).containsExactly("Parlophone");
    }

    @Test
    void importTheRecordingItsFingerprintMatchesClosely()
            throws IOException, InvalidFormatException, InterruptedException {
        // The tags say A Whisper, but AcoustID knows the audio as another recording, which wins.
        final var ingest = storeIngest(flacFile(whisperMetadata()), "09 A Whisper.flac");
        final var stubId = UUID.randomUUID();
        // Above the stub in src/test/resources/wiremock/mappings that matches every fingerprint with nothing.
        musicBrainz.stubFor(post(urlPathEqualTo("/v2/lookup"))
                .withId(stubId)
                .atPriority(1)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("acoustid/lower-your-eyelids-to-die-with-the-sun.json")));
        try {
            engine.processTrack(ingest);
        } finally {
            // The stub server is shared with other tests.
            musicBrainz.removeStubMapping(musicBrainz.getStubMapping(stubId).getItem());
        }

        assertThat(reloadIngest(ingest).state()).isEqualTo(IngestState.COMPLETED);
        final var track = loadTrack(ingest);
        assertThat(track.name()).isEqualTo("Lower Your Eyelids to Die With the Sun");
        assertThat(track.position()).isEqualTo(15);
        final var release = releaseDao
                .loadByMusicBrainzId(UUID.fromString("db85c244-53e7-441c-bab0-52c9c0d27450"))
                .orElseThrow();
        assertThat(release.name()).isEqualTo("Before the Dawn Heals Us");
        assertThat(artistDao.loadByMusicBrainzId(UUID.fromString("6d7b7cd4-254b-4c25-83f6-dd20f98ceacd")))
                .hasValueSatisfying(artist -> assertThat(track.artistId()).isEqualTo(artist.artistId()));
    }

    @Test
    void rejectCorruptAudio() throws IOException, InvalidFormatException {
        final var outputStream = new ByteArrayOutputStream();
        new FlacWriter().writeMetadata(whisperMetadata(), outputStream);
        // Bytes that aren't an audio frame where the first frame should start.
        outputStream.write(new byte[256]);
        final var ingest = storeIngest(outputStream.toByteArray(), "09 A Whisper.flac");

        assertThatThrownBy(() -> engine.processTrack(ingest)).isInstanceOf(InvalidFormatException.class);
        assertThat(reloadIngest(ingest).state()).isEqualTo(IngestState.FAILED);
    }

    @Test
    void requireInputForUnmatchedTrack() throws IOException, InvalidFormatException, InterruptedException {
        final var flac = flacFile(newMetadata("Zzyzx Qwerty Nonexistent Song", "Nobody Atall"));
        final var ingest = storeIngest(flac, "unknown.flac");

        engine.processTrack(ingest);

        assertThat(reloadIngest(ingest).state()).isEqualTo(IngestState.INPUT_REQUIRED);
        // The file stays in the ingest area until the user supplies metadata.
        try (final var stillThere = library.readIngestTrack(ingest)) {
            assertThat(Channels.newInputStream(stillThere).readAllBytes()).isNotEmpty();
        }
    }

    @Test
    void retryOnlyTheMoveAfterItFails() throws IOException, InvalidFormatException, InterruptedException {
        final var artist = artistDao.upsert(new Artist(
                uuidv7(), "Coldplay", "GB", null, 0, UUID.fromString("cc197bad-dc9c-440d-a5b5-d52ba2e14234")));
        final var releaseGroup = releaseGroupDao.upsert(new ReleaseGroup(
                uuidv7(),
                "A Rush of Blood to the Head",
                null,
                null,
                artist.artistId(),
                0,
                UUID.fromString("120c786d-a3b2-3c19-b4ff-2b7b3b4435bf")));
        // A file where the release group's folder belongs makes the move fail.
        final var blocker = libraryProperties
                .mediaFolder()
                .resolve(releaseGroup.releaseGroupId() + "[A Rush of Blood to the Head]");
        Files.createDirectories(blocker.getParent());
        Files.createFile(blocker);
        final var ingest = storeIngest(flacFile(whisperMetadata()), "09 A Whisper.flac");

        assertThatThrownBy(() -> engine.processTrack(ingest)).isInstanceOf(IOException.class);
        final var failed = reloadIngest(ingest);
        assertThat(failed.state()).isEqualTo(IngestState.MOVE_FAILED);
        // The track's file isn't in the library yet.
        assertThat(loadRelease().trackCount()).isZero();

        Files.delete(blocker);
        engine.processTrack(failed);

        assertThat(reloadIngest(ingest).state()).isEqualTo(IngestState.COMPLETED);
        assertThat(loadRelease().trackCount()).isEqualTo(1);
        try (final var imported = library.trackFile(loadTrack(ingest)).getInputStream()) {
            assertThat(imported.readAllBytes()).isEqualTo(flacFile(whisperMetadata()));
        }
    }

    @Test
    void rejectUnsupportedFormat() {
        // The library refuses to store such a file, so the ingest is inserted as it would be by an older upload.
        final var uploader =
                new Users(uuidv7(), UserType.LOCAL, "uploader-" + uuidv7(), UserRole.ADMIN, AccountState.ACTIVE);
        userDao.insert(uploader);
        final var group = new IngestGroup(uuidv7(), IngestGroupKind.ALBUM, "Uploads", uploader.userId());
        ingestDao.insertGroup(group);
        final var ingest = new TrackToIngest(uuidv7(), group.ingestGroupId(), "song.ogg", IngestState.PENDING);
        ingestDao.insertTrackIngest(ingest);

        assertThatThrownBy(() -> engine.processTrack(ingest))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessage("Extension ogg is not supported!");
        assertThat(reloadIngest(ingest).state()).isEqualTo(IngestState.FAILED);
    }

    private static RawMetadata whisperMetadata() {
        final var metadata = newMetadata("A Whisper", "Coldplay");
        metadata.addTag(Tag.TRACK_NUMBER, new TagValue.Int(9));
        return metadata;
    }

    private static RawMetadata newMetadata(final String title, final String artist) {
        final var metadata = new RawMetadata(Duration.ofSeconds(10));
        metadata.addTag(Tag.TRACK_TITLE, new TagValue.Str(title));
        metadata.addTag(Tag.TRACK_ARTIST, new TagValue.Str(artist));
        return metadata;
    }

    private TrackToIngest storeIngest(final byte[] flac, final String fileName)
            throws InvalidFormatException, IOException {
        final var uploader =
                new Users(uuidv7(), UserType.LOCAL, "uploader-" + uuidv7(), UserRole.ADMIN, AccountState.ACTIVE);
        userDao.insert(uploader);
        final var group = new IngestGroup(uuidv7(), IngestGroupKind.ALBUM, "Uploads", uploader.userId());
        ingestDao.insertGroup(group);
        return library.storeIngestTrack(group, fileName, new ByteArrayInputStream(flac));
    }

    private TrackToIngest reloadIngest(final TrackToIngest ingest) {
        return ingestDao.loadTrackIngestById(ingest.trackIngestId());
    }

    /**
     * @return the release A Whisper is on, as stored
     */
    private Release loadRelease() {
        return releaseDao
                .loadByMusicBrainzId(UUID.fromString("5b59d66b-8902-4350-b2e3-324037ef4cb0"))
                .orElseThrow();
    }

    private Track loadTrack(final TrackToIngest ingest) {
        return trackDao.loadByIngest(ingest.trackIngestId());
    }
}
