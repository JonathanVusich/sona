package org.sona.controller;

import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sona.controller.response.TrackIngestResponse;
import org.sona.engine.IngestWorker;
import org.sona.exception.InvalidFormatException;
import org.sona.library.Library;
import org.sona.mappers.TrackIngestResponseMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Uploads into the ingest area. Each file of an upload is sent on its own, into its group, so a large folder isn't one
 * request and a file that fails doesn't fail the rest.
 * <p>
 * A group is the files the uploader says belong together, such as one album. Matching uses that as a hint for choosing
 * one release for them all, since the files' tags don't always settle it (docs/VISION.md, Ingestion). The uploader
 * makes up the group's ID, a UUIDv7, and sends every file of the group under it: nothing is stored for a group but the
 * ID its files share.
 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('library:upload')")
public class IngestController {

    private final Library library;
    private final IngestWorker worker;
    private final TrackIngestResponseMapper mapper;

    /**
     * Stores one file of an upload, sent as the raw request body, to be ingested in the background. The body must be
     * sent as {@code application/octet-stream}: a form type would have the server read the file as form fields.
     *
     * @param groupIngestId the ID the uploader made up for the file's group
     * @param filename      the file's name where it was uploaded from, whose extension says its format
     */
    @PostMapping(path = ApiRoutes.INGEST_GROUP_TRACKS, consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TrackIngestResponse uploadTrack(
            @PathVariable final UUID groupIngestId,
            @RequestParam @NotBlank final String filename,
            final InputStream body)
            throws InvalidFormatException, IOException {
        final var ingest = library.storeIngestTrack(groupIngestId, filename, body);
        worker.processPending();
        return mapper.toResponse(ingest);
    }
}
