package org.sona.samples;

import static org.sona.utils.IDGenerator.uuidv7;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import lombok.experimental.UtilityClass;
import org.sona.db.IngestDao;
import org.sona.library.Library;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.IngestGroup;
import org.sona.model.tables.pojos.TrackToIngest;

/**
 * Seeds the ingest of files in a group the way an uploader does: its ingest is created first, then its bytes are sent.
 */
@UtilityClass
public class IngestUploadUtils {

    /**
     * @return the ingest of a file of the group, created and waiting for its bytes
     */
    public static TrackToIngest createIngest(
            final IngestDao ingestDao, final IngestGroup group, final String filename) {
        final var ingest = new TrackToIngest(uuidv7(), group.ingestGroupId(), filename, IngestState.CREATED);
        ingestDao.insertTrackIngest(ingest);
        return ingest;
    }

    /**
     * @return the ingest, now uploaded
     */
    public static TrackToIngest upload(final Library library, final TrackToIngest ingest, final byte[] file)
            throws IOException {
        return library.storeIngestTrack(ingest.groupIngestId(), ingest.trackIngestId(), new ByteArrayInputStream(file));
    }

    /**
     * @return the ingest of a file of the group that has been created and uploaded
     */
    public static TrackToIngest createAndUpload(
            final IngestDao ingestDao,
            final Library library,
            final IngestGroup group,
            final String filename,
            final byte[] file)
            throws IOException {
        return upload(library, createIngest(ingestDao, group, filename), file);
    }
}
