package org.sona.format.flac;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.sona.exception.InvalidFormatException;

/**
 * Times decoding the first two minutes of a FLAC file, as much as the fingerprinter reads, from memory so that only
 * the decoder is measured. Run with `./gradlew :server:jmh -PbenchmarkFlac=<path>`.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 2)
@Fork(2)
public class FlacDecoderBenchmark {

    private static final int SECONDS = 120;

    @Param("")
    public String file;

    private byte[] bytes;

    @Setup
    public void readFile() throws IOException {
        if (file.isBlank()) {
            throw new IllegalArgumentException("Pass the FLAC file to decode with -PbenchmarkFlac=<path>");
        }
        bytes = Files.readAllBytes(Path.of(file));
    }

    @Benchmark
    public void decodeTwoMinutes(final Blackhole blackhole) throws IOException, InvalidFormatException {
        final var decoder = new FlacParser().decodeAudio(new ByteArrayInputStream(bytes));
        final var samples = (long) decoder.streamInfo().sampleRate() * SECONDS;
        var decoded = 0L;
        for (final var frame : decoder) {
            blackhole.consume(frame);
            decoded += frame.sampleCount();
            if (decoded >= samples) {
                break;
            }
        }
    }
}
