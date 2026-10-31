package org.sona.controller;

import static org.sona.utils.IDGenerator.uuidv7;

import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sona.auth.UserAuthentication;
import org.sona.controller.request.IngestGroupRequest;
import org.sona.controller.response.IngestGroupResponse;
import org.sona.controller.response.TrackIngestResponse;
import org.sona.db.IngestDao;
import org.sona.exception.InvalidFormatException;
import org.sona.library.Library;
import org.sona.mappers.IngestGroupResponseMapper;
import org.sona.mappers.TrackIngestResponseMapper;
import org.sona.model.enums.IngestGroupState;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.IngestGroup;
import org.sona.model.tables.pojos.TrackToIngest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Uploads into the ingest area. Once the uploader has settled which files belong together, it creates each group with
 * all of its files, which Sona gives IDs, then sends each file on its own to its ID, so a large folder isn't one
 * request and a file that fails doesn't fail the rest. Once every file of a group is uploaded the ingest worker
 * matches them in the background.
 * <p>
 * A group is the files the uploader says belong together, such as one album, or the loose files it doesn't know how to
 * group. Matching uses that as a hint for choosing one release for them all, since the files' tags don't always settle
 * it (docs/VISION.md, Ingestion).
 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('library:upload')")
public class IngestController {

    private final Library library;
    private final IngestDao ingestDao;
    private final IngestGroupResponseMapper ingestGroupResponseMapper;
    private final TrackIngestResponseMapper mapper;

    /**
     * Creates a group, and an ingest for each file in it, under new IDs which the files are then uploaded to. Only its
     * uploader is told how they are ingested.
     */
    @PostMapping(ApiRoutes.INGEST_GROUPS)
    @ResponseStatus(HttpStatus.CREATED)
    public IngestGroupResponse createIngestGroup(
            final UserAuthentication authentication, @RequestBody @Valid final IngestGroupRequest request)
            throws InvalidFormatException {
        for (final var filename : request.filenames()) {
            library.checkIngestFormat(filename);
        }
        final var group = new IngestGroup(
                uuidv7(), request.kind(), request.name(), authentication.user().userId(), IngestGroupState.CREATED);
        final var trackIngests = request.filenames().stream()
                .map(filename -> new TrackToIngest(uuidv7(), group.ingestGroupId(), filename, IngestState.CREATED))
                .toList();
        ingestDao.insertGroup(group, trackIngests);
        return ingestGroupResponseMapper.toResponse(group, trackIngests);
    }

    /**
     * Stores one file of an upload, sent as the raw request body, once; if the connection breaks it may be sent
     * again. The body must be sent as {@code application/octet-stream}: a form type would have the server read the
     * file as form fields.
     *
     * @param groupIngestId the ID Sona gave the file's group when the uploader created it
     * @param trackIngestId the ID Sona gave the file when the uploader created its group
     */
    @PutMapping(path = ApiRoutes.INGEST_GROUP_TRACK, consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public TrackIngestResponse uploadTrack(
            @PathVariable final UUID groupIngestId, @PathVariable final UUID trackIngestId, final InputStream body)
            throws IOException {
        final var ingest = library.storeIngestTrack(groupIngestId, trackIngestId, body);
        return mapper.toResponse(ingest);
    }
}
