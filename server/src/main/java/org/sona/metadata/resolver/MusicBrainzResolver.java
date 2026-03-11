package org.sona.metadata.resolver;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.sona.metadata.RawMetadata;
import org.sona.model.tables.pojos.Track;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public final class MusicBrainzResolver implements MetadataResolver {

    private final DSLContext dsl;

    @Override
    public Optional<Track> resolveTrack(final RawMetadata metadata) {
        return Optional.empty();
    }
}
