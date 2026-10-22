package org.sona.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sona.controller.request.IngestGroupRequest;
import org.sona.controller.response.TrackIngestResponse;
import org.sona.db.IngestGroupDao;
import org.sona.exception.IngestGroupNotFoundException;
import org.sona.exception.InvalidFormatException;
import org.sona.library.Library;
import org.sona.mappers.TrackIngestResponseMapper;
import org.sona.model.tables.pojos.IngestGroup;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Uploads into the ingest area. Once the uploader has settled which files belong together, it creates each group under
 * an ID it makes up, a UUIDv7, then sends each of the group's files on its own, so a large folder isn't one request and
 * a file that fails doesn't fail the rest. The ingest worker processes them in the background.
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
    private final IngestGroupDao ingestGroupDao;
    private final TrackIngestResponseMapper mapper;

    /**
     * Creates the group, or updates it when sent again, such as after a connection broke.
     *
     * @param groupIngestId the ID the uploader made up for the group
     */
    @PutMapping(ApiRoutes.INGEST_GROUP)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void putIngestGroup(
            @PathVariable final UUID groupIngestId, @RequestBody @Valid final IngestGroupRequest request) {
        ingestGroupDao.upsert(new IngestGroup(groupIngestId, request.kind(), request.name()));
    }

    /**
     * Stores one file of an upload, sent as the raw request body, to be ingested in the background. The body must be
     * sent as {@code application/octet-stream}: a form type would have the server read the file as form fields.
     *
     * @param groupIngestId the ID of the file's group, which the uploader created first
     * @param filename      the file's name where it was uploaded from, whose extension says its format
     */
    @PostMapping(path = ApiRoutes.INGEST_GROUP_TRACKS, consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TrackIngestResponse uploadTrack(
            @PathVariable final UUID groupIngestId,
            @RequestParam @NotBlank final String filename,
            final InputStream body)
            throws InvalidFormatException, IOException {
        final var group = ingestGroupDao
                .loadById(groupIngestId)
                .orElseThrow(() -> new IngestGroupNotFoundException(groupIngestId));
        final var ingest = library.storeIngestTrack(group, filename, body);
        return mapper.toResponse(ingest);
    }
}
