package org.sona.db;

import java.util.List;
import org.sona.model.tables.pojos.IngestGroup;
import org.sona.model.tables.pojos.TrackToIngest;

/**
 * A group whose files are all uploaded, with those still waiting to be matched, oldest first.
 */
public record UploadedGroup(IngestGroup group, List<TrackToIngest> trackIngests) {}
