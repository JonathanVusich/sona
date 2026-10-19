package org.sona.controller;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.sona.controller.response.ErrorCode;
import org.sona.controller.response.IngestGroupResponse;
import org.sona.controller.response.TrackIngestResponse;
import org.sona.engine.IngestWorker;
import org.sona.engine.IngestionEngine;
import org.sona.exception.ClientException;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import static org.sona.utils.IDGenerator.uuidv7;

/**
 * Uploads into the ingest area. Each file of an upload is sent on its own, into the group the upload started, so a
 * large folder isn't one request and a file that fails doesn't fail the rest.
 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('library:upload')")
public class IngestController {

    private final Library library;
    private final IngestionEngine engine;
    private final IngestWorker worker;
    private final TrackIngestResponseMapper mapper;

    /**
     * Starts an upload. Nothing is stored until its first file is: a group is only the ID its files share.
     */
    @PostMapping(ApiRoutes.INGEST_GROUPS)
    public IngestGroupResponse startUpload() {
        return new IngestGroupResponse(uuidv7());
    }

    /**
     * Stores one file of an upload, sent as the raw request body, to be ingested in the background. The body must be
     * sent as {@code application/octet-stream}: a form type would have the server read the file as form fields.
     *
     * @param filename the file's name where it was uploaded from, whose extension says its format
     */
    @PostMapping(path = ApiRoutes.INGEST_GROUP_TRACKS, consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TrackIngestResponse uploadTrack(@PathVariable final UUID groupIngestId,
                                           @RequestParam @NotBlank final String filename,
                                           final InputStream body) throws IOException {
        if (!engine.supports(filename)) {
            throw new ClientException(ErrorCode.UNSUPPORTED_FORMAT);
        }
        final var ingest = library.storeIngestTrack(groupIngestId, filename, body);
        worker.notifyPending();
        return mapper.toResponse(ingest);
    }
}
