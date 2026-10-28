package org.sona.engine;

import static java.util.Map.entry;
import static java.util.stream.Collectors.toMap;
import static org.sona.utils.IDGenerator.uuidv7;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.sona.db.ArtistDao;
import org.sona.db.IngestDao;
import org.sona.db.ReleaseDao;
import org.sona.db.ReleaseGroupDao;
import org.sona.db.TrackDao;
import org.sona.exception.InvalidFormatException;
import org.sona.fingerprint.Fingerprint;
import org.sona.format.FloatPcmAudio;
import org.sona.format.Format;
import org.sona.format.IntPcmAudio;
import org.sona.format.MeasuredAudio;
import org.sona.format.MeasuredFloatAudio;
import org.sona.format.MeasuredIntAudio;
import org.sona.format.Parser;
import org.sona.format.PcmAudio;
import org.sona.format.flac.FlacParser;
import org.sona.format.mp3.Mp3Parser;
import org.sona.library.Library;
import org.sona.metadata.RawMetadata;
import org.sona.metadata.resolver.MetadataResolver;
import org.sona.metadata.resolver.ResolvedArtist;
import org.sona.metadata.resolver.ResolvedLabel;
import org.sona.metadata.resolver.ResolvedMedium;
import org.sona.metadata.resolver.ResolvedRelease;
import org.sona.metadata.resolver.ResolvedTrack;
import org.sona.metadata.resolver.TrackCandidate;
import org.sona.metadata.resolver.TrackResolution;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.Artist;
import org.sona.model.tables.pojos.IngestCandidate;
import org.sona.model.tables.pojos.Label;
import org.sona.model.tables.pojos.Medium;
import org.sona.model.tables.pojos.Release;
import org.sona.model.tables.pojos.ReleaseGroup;
import org.sona.model.tables.pojos.ReleaseLabel;
import org.sona.model.tables.pojos.Track;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public final class DefaultIngestionEngine implements IngestionEngine {

    private static final Map<String, Parser> PARSERS = Stream.of(new FlacParser(), new Mp3Parser())
            .flatMap(p -> p.format().getExtensions().stream().map(ext -> entry(ext, p)))
            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

    private final Library library;
    private final MetadataResolver resolver;
    private final IngestDao ingestDao;
    private final IngestProgressPublisher progressPublisher;
    private final ArtistDao artistDao;
    private final ReleaseGroupDao releaseGroupDao;
    private final ReleaseDao releaseDao;
    private final TrackDao trackDao;

    @Override
    public void processTrack(final TrackToIngest trackToIngest)
            throws InvalidFormatException, IOException, InterruptedException {
        switch (trackToIngest.state()) {
            case PENDING, INPUT_REQUIRED -> ingestTrack(trackToIngest);
            case MOVE_FAILED -> retryMove(trackToIngest);
            case FAILED ->
                throw new IllegalStateException("Track %s can't be imported".formatted(trackToIngest.trackIngestId()));
            case COMPLETED ->
                throw new IllegalStateException(
                        "Track %s has already been imported".formatted(trackToIngest.trackIngestId()));
        }
    }

    private void ingestTrack(final TrackToIngest trackToIngest)
            throws InvalidFormatException, IOException, InterruptedException {
        final Parser parser;
        final ParsedTrack parsed;
        try {
            parser = parserFor(trackToIngest.originalFileName());
            parsed = parseIngestTrack(trackToIngest, parser);
        } catch (InvalidFormatException e) {
            // Processing it again would fail the same way.
            markState(trackToIngest, IngestState.FAILED);
            throw e;
        }
        final var metadata = parsed.metadata();
        switch (resolver.resolveTrack(trackToIngest, metadata, parsed.fingerprint())) {
            case TrackResolution.Matched matched ->
                importTrack(trackToIngest, matched.track(), metadata.duration(), parser.format());
            case TrackResolution.NeedsReview review -> requireInput(trackToIngest, review.candidates());
        }
    }

    // The candidates are stored first, so whoever sees the state finds them.
    private void requireInput(final TrackToIngest trackToIngest, final List<TrackCandidate> candidates) {
        ingestDao.updateCandidates(trackToIngest.trackIngestId(), newIngestCandidates(trackToIngest, candidates));
        markState(trackToIngest, IngestState.INPUT_REQUIRED);

        // TODO: Refactor to support multiple metadata providers using plugins
    }

    private static List<IngestCandidate> newIngestCandidates(
            final TrackToIngest trackToIngest, final List<TrackCandidate> candidates) {
        return IntStream.range(0, candidates.size())
                .mapToObj(index -> newIngestCandidate(trackToIngest, candidates.get(index), index + 1))
                .toList();
    }

    private static IngestCandidate newIngestCandidate(
            final TrackToIngest trackToIngest, final TrackCandidate candidate, final int rank) {
        final var track = candidate.track();
        return new IngestCandidate(
                uuidv7(),
                trackToIngest.trackIngestId(),
                rank,
                candidate.score(),
                track.recordingId(),
                track.title(),
                track.artist().artistId(),
                track.artist().name(),
                track.releaseId(),
                candidate.releaseTitle(),
                track.discNumber(),
                track.trackNumber());
    }

    private static Parser parserFor(final String filename) throws InvalidFormatException {
        final var extension = fileExtension(filename);
        final var parser = PARSERS.get(extension);
        if (parser == null) {
            throw new InvalidFormatException("Extension %s is not supported!".formatted(extension));
        }
        return parser;
    }

    private static String fileExtension(final String filename) {
        return FilenameUtils.getExtension(filename).toLowerCase(Locale.ROOT);
    }

    // The audio is fingerprinted while the file is open, as it's decoded from the channel its metadata was read from.
    private ParsedTrack parseIngestTrack(final TrackToIngest trackToIngest, final Parser parser)
            throws IOException, InvalidFormatException {
        try (final var channel = library.readIngestTrack(trackToIngest)) {
            final var parsed = parser.parse(channel);
            return measuredTrack(parsed.metadata(), parsed.audio());
        }
    }

    // The duration is of the decoded audio, not the header's claim of it, which a bad encoder or an edit can leave
    // wrong.
    private static ParsedTrack measuredTrack(final RawMetadata metadata, final PcmAudio audio)
            throws IOException, InvalidFormatException {
        return switch (audio) {
            case IntPcmAudio ints -> {
                final var measured = new MeasuredIntAudio(ints);
                yield measuredTrack(metadata, measured, new Fingerprint(measured));
            }
            case FloatPcmAudio floats -> {
                final var measured = new MeasuredFloatAudio(floats);
                yield measuredTrack(metadata, measured, new Fingerprint(measured));
            }
        };
    }

    private static ParsedTrack measuredTrack(
            final RawMetadata metadata, final MeasuredAudio<?> audio, final Fingerprint fingerprint)
            throws IOException, InvalidFormatException {
        // The fingerprint only decodes the start of the audio.
        audio.readToEnd();
        return new ParsedTrack(metadata.withDuration(audio.duration()), fingerprint);
    }

    private record ParsedTrack(RawMetadata metadata, Fingerprint fingerprint) {}

    private void importTrack(
            final TrackToIngest trackToIngest,
            final ResolvedTrack resolved,
            final Duration duration,
            final Format format)
            throws IOException {
        final var track = storeTrack(trackToIngest, resolved, duration, format);
        moveIntoLibrary(trackToIngest, track);
    }

    private void retryMove(final TrackToIngest trackToIngest) throws IOException {
        // The metadata was stored before the move failed, so only the move is retried.
        final var track = trackDao.loadByIngest(trackToIngest.trackIngestId());
        moveIntoLibrary(trackToIngest, track);
    }

    private void moveIntoLibrary(final TrackToIngest trackToIngest, final Track track) throws IOException {
        try {
            library.importTrack(trackToIngest, track.path());
        } catch (IOException e) {
            markState(trackToIngest, IngestState.MOVE_FAILED);
            throw e;
        }
        markState(trackToIngest, IngestState.COMPLETED);
    }

    private void markState(final TrackToIngest trackToIngest, final IngestState state) {
        ingestDao.updateState(trackToIngest.trackIngestId(), state);
        progressPublisher.publish(trackToIngest, state);
    }

    /**
     * Stores the track together with the artist, release group, release and medium rows it references. Each row
     * commits on its own: the upserts are idempotent, so if the track insert fails, processing the ingest again reuses
     * the rows.
     * A release or artist already stored isn't looked up again, which saves MusicBrainz requests.
     */
    private Track storeTrack(
            final TrackToIngest trackToIngest,
            final ResolvedTrack resolved,
            final Duration duration,
            final Format format) {
        // Tracks from the same MusicBrainz release share its rows, and so its library folder.
        final var release = releaseDao
                .loadByMusicBrainzId(resolved.releaseId())
                .orElseGet(() -> storeRelease(resolver.resolveRelease(resolved.releaseId())));
        final var releaseGroup =
                releaseGroupDao.loadById(release.releaseGroupId()).orElseThrow();
        final var medium = releaseDao
                .loadMediumByPosition(release.releaseId(), resolved.discNumber())
                .orElseGet(() -> completeRelease(release, resolved));
        final var artistId = resolved.artist().artistId();
        final var artist =
                artistDao.loadByMusicBrainzId(artistId).orElseGet(() -> upsertArtist(resolver.resolveArtist(artistId)));
        final var track = newTrack(trackToIngest, resolved, artist, releaseGroup, release, medium, duration, format);
        trackDao.insert(track);
        return track;
    }

    private Release storeRelease(final ResolvedRelease resolved) {
        final var group = resolved.releaseGroup();
        final var groupArtist = upsertArtist(group.artist());
        final var releaseGroup = releaseGroupDao.upsert(new ReleaseGroup(
                uuidv7(),
                group.title(),
                group.type(),
                group.firstReleaseDate(),
                groupArtist.artistId(),
                0,
                group.releaseGroupId()));
        final var releaseArtist = upsertArtist(resolved.artist());
        final var release = releaseDao.upsert(new Release(
                uuidv7(),
                resolved.title(),
                null,
                resolved.date(),
                resolved.country(),
                0,
                releaseGroup.releaseGroupId(),
                releaseArtist.artistId(),
                resolved.releaseId()));
        storeReleaseParts(release, resolved);
        return release;
    }

    /**
     * Stores the release's labels and then its media. The media go last: a stored release without them is one whose
     * ingest stopped part-way, which {@link #completeRelease} finishes.
     */
    private List<Medium> storeReleaseParts(final Release release, final ResolvedRelease resolved) {
        storeLabels(release, resolved.labels());
        return storeMedia(release, resolved.media());
    }

    private void storeLabels(final Release release, final List<ResolvedLabel> resolvedLabels) {
        final var labels = releaseDao.upsertLabels(resolvedLabels.stream()
                .map(label -> new Label(uuidv7(), label.name(), label.labelId()))
                .toList());
        releaseDao.upsertReleaseLabels(labels.stream()
                .map(label -> new ReleaseLabel(release.releaseId(), label.labelId()))
                .toList());
    }

    /**
     * Finishes storing a release whose ingest stopped before its media were stored, so its labels may be missing too:
     * looks it up again and stores its labels and media. Both upserts are idempotent.
     *
     * @return the track's medium
     */
    private Medium completeRelease(final Release release, final ResolvedTrack resolved) {
        final var media = storeReleaseParts(release, resolver.resolveRelease(resolved.releaseId()));
        return media.stream()
                .filter(medium -> medium.position() == resolved.discNumber())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Release %s has no medium %d".formatted(release.releaseId(), resolved.discNumber())));
    }

    private List<Medium> storeMedia(final Release release, final List<ResolvedMedium> media) {
        return releaseDao.upsertMedia(media.stream()
                .map(medium -> new Medium(uuidv7(), release.releaseId(), medium.position(), medium.format()))
                .toList());
    }

    private Artist upsertArtist(final ResolvedArtist artist) {
        return artistDao.upsert(new Artist(uuidv7(), artist.name(), artist.country(), null, 0, artist.artistId()));
    }

    private Track newTrack(
            final TrackToIngest trackToIngest,
            final ResolvedTrack resolved,
            final Artist artist,
            final ReleaseGroup releaseGroup,
            final Release release,
            final Medium medium,
            final Duration duration,
            final Format format) {
        final var trackId = uuidv7();
        return new Track(
                trackId,
                resolved.title(),
                Math.toIntExact(duration.toSeconds()),
                format.toAudioFormat(),
                library.libraryPath(releaseGroup, release, trackId, resolved.title(), trackToIngest),
                medium.mediumId(),
                resolved.trackNumber(),
                artist.artistId(),
                trackToIngest.trackIngestId());
    }
}
