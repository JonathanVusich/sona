package org.sona.metadata.resolver;

import dev.javax.bitstream.BitInputStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sona.IntegrationTest;
import org.sona.exception.InvalidFormatException;
import org.sona.format.flac.Flac;
import org.sona.model.enums.IngestState;
import org.sona.model.tables.pojos.TrackToIngest;
import org.sona.samples.FlacSample;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

import static org.sona.utils.IDGenerator.uuidv7;

@IntegrationTest
class MusicBrainzResolverTest {

    @Autowired
    private MusicBrainzResolver resolver;

    @ParameterizedTest
    @EnumSource(value = FlacSample.class, names = {"SAMPLE_3"}, mode = EnumSource.Mode.EXCLUDE)
    void resolveTrack(FlacSample sample) throws IOException, InvalidFormatException, InterruptedException {

        final var trackToIngest = new TrackToIngest(uuidv7(), uuidv7(), sample.fileName(), IngestState.PENDING);
        final var rawMetadata = new Flac().parse(sample.inputStream());

        final var track = resolver.resolveTrack(trackToIngest, rawMetadata);
    }

}