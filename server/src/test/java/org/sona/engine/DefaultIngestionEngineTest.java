package org.sona.engine;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.config.properties.LibraryProperties;
import org.sona.db.ArtistDao;
import org.sona.db.ReleaseDao;
import org.sona.db.ReleaseGroupDao;
import org.sona.db.TrackDao;
import org.sona.db.TrackToIngestDao;
import org.sona.exception.InvalidFormatException;
import org.sona.library.Library;
import org.sona.metadata.RawMetadata;
import org.sona.metadata.Tag;
import org.sona.metadata.TagValue;
import org.sona.model.enums.AudioFormat;
import org.sona.model.enums.IngestState;
import org.sona.model.enums.ReleaseGroupType;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.Medium;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
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

@IntegrationTest
class DefaultIngestionEngineTest {

    @Autowired
    private IngestionEngine engine;
    @Autowired
    private Library library;
    @Autowired
    private LibraryProperties libraryProperties;
    @Autowired
    private TrackToIngestDao trackToIngestDao;
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
                .join(LABEL).on(LABEL.LABEL_ID.eq(RELEASE_LABEL.LABEL_ID))
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

        assertThat(track.path()).isEqualTo("%s[A Rush of Blood to the Head]/%s[A Rush of Blood to the Head]/%s[A Whisper].flac"
                .formatted(releaseGroup.releaseGroupId(), release.releaseId(), track.trackId()));

        try (final var imported = library.readTrack(track)) {
            assertThat(imported.readAllBytes()).isEqualTo(flac);
        }
        assertThatThrownBy(() -> library.readIngestTrack(ingest)).isInstanceOf(NoSuchFileException.class);
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
        assertThat(musicBrainz.findAll(getRequestedFor(urlPathMatching("/ws/2/release/.*")))).hasSize(1);
    }

    @Test
    void storeTheMediaOfAReleaseStoredWithoutThem() throws IOException, InvalidFormatException, InterruptedException {
        final var artist = artistDao.upsert(new Artist(uuidv7(), "Coldplay", "GB", null,
                UUID.fromString("cc197bad-dc9c-440d-a5b5-d52ba2e14234")));
        final var releaseGroup = releaseGroupDao.upsert(new ReleaseGroup(uuidv7(), "A Rush of Blood to the Head", null,
                null, artist.artistId(), UUID.fromString("120c786d-a3b2-3c19-b4ff-2b7b3b4435bf")));
        final var release = releaseDao.upsert(new Release(uuidv7(), "A Rush of Blood to the Head", null, null, null, 0,
                releaseGroup.releaseGroupId(), artist.artistId(),
                UUID.fromString("5b59d66b-8902-4350-b2e3-324037ef4cb0")));
        final var ingest = storeIngest(flacFile(whisperMetadata()), "09 A Whisper.flac");

        engine.processTrack(ingest);

        final var medium = dsl.selectFrom(MEDIUM)
                .where(MEDIUM.MEDIUM_ID.eq(loadTrack(ingest).mediumId()))
                .fetchSingleInto(Medium.class);
        assertThat(medium).extracting(Medium::releaseId, Medium::position, Medium::format)
                .containsExactly(release.releaseId(), 1, "Digital Media");
    }

    @Test
    void requireInputForUnmatchedTrack()throws IOException, InvalidFormatException, InterruptedException {
        final var flac = flacFile(newMetadata("Zzyzx Qwerty Nonexistent Song", "Nobody Atall"));
        final var ingest = storeIngest(flac, "unknown.flac");

        engine.processTrack(ingest);

        assertThat(reloadIngest(ingest).state()).isEqualTo(IngestState.INPUT_REQUIRED);
        // The file stays in the ingest area until the user supplies metadata.
        try (final var stillThere = library.readIngestTrack(ingest)) {
            assertThat(stillThere.readAllBytes()).isNotEmpty();
        }
    }

    @Test
    void retryOnlyTheMoveAfterItFails() throws IOException, InvalidFormatException, InterruptedException {
        final var artist = artistDao.upsert(new Artist(uuidv7(), "Coldplay", "GB", null,
                UUID.fromString("cc197bad-dc9c-440d-a5b5-d52ba2e14234")));
        final var releaseGroup = releaseGroupDao.upsert(new ReleaseGroup(uuidv7(), "A Rush of Blood to the Head", null,
                null, artist.artistId(), UUID.fromString("120c786d-a3b2-3c19-b4ff-2b7b3b4435bf")));
        // A file where the release group's folder belongs makes the move fail.
        final var blocker = libraryProperties.mediaFolder()
                .resolve(releaseGroup.releaseGroupId() + "[A Rush of Blood to the Head]");
        Files.createDirectories(blocker.getParent());
        Files.createFile(blocker);
        final var ingest = storeIngest(flacFile(whisperMetadata()), "09 A Whisper.flac");

        assertThatThrownBy(() -> engine.processTrack(ingest)).isInstanceOf(IOException.class);
        final var failed = reloadIngest(ingest);
        assertThat(failed.state()).isEqualTo(IngestState.MOVE_FAILED);

        Files.delete(blocker);
        engine.processTrack(failed);

        assertThat(reloadIngest(ingest).state()).isEqualTo(IngestState.COMPLETED);
        try (final var imported = library.readTrack(loadTrack(ingest))) {
            assertThat(imported.readAllBytes()).isEqualTo(flacFile(whisperMetadata()));
        }
    }

    @Test
    void rejectUnsupportedFormat() throws IOException {
        final var ingest = storeIngest(flacFile(new RawMetadata(Duration.ofSeconds(10))), "song.mp3");

        assertThatThrownBy(() -> engine.processTrack(ingest))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessage("Extension mp3 is not supported!");
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

    private TrackToIngest storeIngest(final byte[] flac, final String fileName) throws IOException {
        final var groupId = uuidv7();
        library.storeIngestTrack(groupId, fileName, new ByteArrayInputStream(flac));
        final var ingests = trackToIngestDao.loadByGroup(groupId);
        assertThat(ingests).hasSize(1);
        return ingests.getFirst();
    }

    private TrackToIngest reloadIngest(final TrackToIngest ingest) {
        return trackToIngestDao.loadById(ingest.trackIngestId());
    }

    private Track loadTrack(final TrackToIngest ingest) {
        return trackDao.loadByIngest(ingest.trackIngestId());
    }
}
