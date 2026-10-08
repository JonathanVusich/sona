package org.sona.engine;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.sona.IntegrationTest;
import org.sona.config.properties.LibraryProperties;
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
import org.sona.model.tables.pojos.Artist;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sona.model.Tables.ARTIST;
import static org.sona.model.Tables.RELEASE;
import static org.sona.model.Tables.RELEASE_GROUP;
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
    // For rows the DAOs have no lookup for.
    @Autowired
    private DSLContext dsl;

    @Test
    void importMatchedTrack() throws IOException, InvalidFormatException, InterruptedException {
        final var flac = flacFile(whisper());
        // Upper-case extension on purpose: extension matching is case-insensitive.
        final var ingest = store(flac, "09 A Whisper.FLAC");

        engine.processTrack(ingest);

        assertThat(load(ingest).state()).isEqualTo(IngestState.COMPLETED);

        final var track = loadTrack(ingest);
        assertThat(track.name()).isEqualTo("A Whisper");
        assertThat(track.duration()).isEqualTo(10);
        assertThat(track.format()).isEqualTo(AudioFormat.FLAC);

        final var release = dsl.selectFrom(RELEASE)
                .where(RELEASE.RELEASE_ID.eq(track.releaseId()))
                .fetchSingleInto(Release.class);
        assertThat(release.musicbrainzReleaseId()).isEqualTo(UUID.fromString("5b59d66b-8902-4350-b2e3-324037ef4cb0"));
        final var releaseGroup = dsl.selectFrom(RELEASE_GROUP)
                .where(RELEASE_GROUP.RELEASE_GROUP_ID.eq(release.releaseGroupId()))
                .fetchSingleInto(ReleaseGroup.class);
        assertThat(releaseGroup.musicbrainzReleaseGroupId())
                .isEqualTo(UUID.fromString("120c786d-a3b2-3c19-b4ff-2b7b3b4435bf"));

        final var artist = dsl.selectFrom(ARTIST)
                .where(ARTIST.ARTIST_ID.eq(track.artistId()))
                .fetchSingleInto(Artist.class);
        assertThat(artist.name()).isEqualTo("Coldplay");
        assertThat(artist.musicbrainzArtistId()).isEqualTo(UUID.fromString("cc197bad-dc9c-440d-a5b5-d52ba2e14234"));
        assertThat(release.artistId()).isEqualTo(artist.artistId());

        assertThat(track.path()).isEqualTo("%s[A Rush of Blood to the Head]/%s[A Rush of Blood to the Head]/%s[A Whisper].flac"
                .formatted(releaseGroup.releaseGroupId(), release.releaseId(), track.trackId()));

        try (final var imported = library.readTrack(track)) {
            assertThat(imported.readAllBytes()).isEqualTo(flac);
        }
        assertThatThrownBy(() -> library.readIngestTrack(ingest)).isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void importTracksFromOneReleaseIntoOneFolder() throws IOException, InvalidFormatException, InterruptedException {
        final var first = store(flacFile(whisper()), "09 A Whisper.flac");
        final var second = store(flacFile(whisper()), "09 A Whisper (copy).flac");
        engine.processTrack(first);
        engine.processTrack(second);

        assertThat(loadTrack(second).releaseId()).isEqualTo(loadTrack(first).releaseId());
        assertThat(dsl.fetchCount(RELEASE)).isEqualTo(1);
        assertThat(dsl.fetchCount(RELEASE_GROUP)).isEqualTo(1);
    }

    @Test
    void requireInputForUnmatchedTrack() throws IOException, InvalidFormatException, InterruptedException {
        final var ingest = store(flacFile(metadata("Zzyzx Qwerty Nonexistent Song", "Nobody Atall")), "unknown.flac");

        engine.processTrack(ingest);

        assertThat(load(ingest).state()).isEqualTo(IngestState.INPUT_REQUIRED);
        // The file stays in the ingest area until the user supplies metadata.
        try (final var stillThere = library.readIngestTrack(ingest)) {
            assertThat(stillThere.readAllBytes()).isNotEmpty();
        }
    }

    @Test
    void retryOnlyTheMoveAfterItFails() throws IOException, InvalidFormatException, InterruptedException {
        final var releaseGroup = releaseGroupDao.upsert(new ReleaseGroup(
                uuidv7(), "A Rush of Blood to the Head", UUID.fromString("120c786d-a3b2-3c19-b4ff-2b7b3b4435bf")));
        // A file where the release group's folder belongs makes the move fail.
        final var blocker = libraryProperties.mediaFolder()
                .resolve(releaseGroup.releaseGroupId() + "[A Rush of Blood to the Head]");
        Files.createDirectories(blocker.getParent());
        Files.createFile(blocker);
        final var ingest = store(flacFile(whisper()), "09 A Whisper.flac");

        assertThatThrownBy(() -> engine.processTrack(ingest)).isInstanceOf(IOException.class);
        final var failed = load(ingest);
        assertThat(failed.state()).isEqualTo(IngestState.MOVE_FAILED);

        Files.delete(blocker);
        engine.processTrack(failed);

        assertThat(load(ingest).state()).isEqualTo(IngestState.COMPLETED);
        try (final var imported = library.readTrack(loadTrack(ingest))) {
            assertThat(imported.readAllBytes()).isEqualTo(flacFile(whisper()));
        }
    }

    @Test
    void rejectUnsupportedFormat() throws IOException {
        final var ingest = store(flacFile(new RawMetadata(Duration.ofSeconds(10))), "song.mp3");

        assertThatThrownBy(() -> engine.processTrack(ingest))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessage("Extension mp3 is not supported!");
        assertThat(load(ingest).state()).isEqualTo(IngestState.FAILED);
    }

    private static RawMetadata whisper() {
        final var metadata = metadata("A Whisper", "Coldplay");
        metadata.add(Tag.TRACK_NUMBER, new TagValue.Int(9));
        return metadata;
    }

    private static RawMetadata metadata(final String title, final String artist) {
        final var metadata = new RawMetadata(Duration.ofSeconds(10));
        metadata.add(Tag.TRACK_TITLE, new TagValue.Str(title));
        metadata.add(Tag.TRACK_ARTIST, new TagValue.Str(artist));
        return metadata;
    }

    private TrackToIngest store(final byte[] flac, final String fileName) throws IOException {
        final var groupId = uuidv7();
        library.storeIngestTrack(groupId, fileName, new ByteArrayInputStream(flac));
        final var ingests = trackToIngestDao.loadByGroup(groupId);
        assertThat(ingests).hasSize(1);
        return ingests.getFirst();
    }

    private TrackToIngest load(final TrackToIngest ingest) {
        return trackToIngestDao.load(ingest.trackIngestId());
    }

    private Track loadTrack(final TrackToIngest ingest) {
        return trackDao.loadByIngest(ingest.trackIngestId());
    }
}
