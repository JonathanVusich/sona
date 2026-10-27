package org.sona.format.flac;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.sona.exception.InvalidFormatException;
import org.sona.exception.UncheckedInvalidFormatException;
import org.sona.fingerprint.Fingerprint;
import org.sona.format.PcmFrame;

/**
 * Fuzzes the FLAC decoder and the fingerprint made from it against {@code flac} and {@code fpcalc}, which
 * {@code reference.sh} runs in a container (built from the {@code Dockerfile} beside it in
 * {@code src/test/flac-fuzz}, whose {@code generate.sh} makes the corpus) over a folder of FLACs, writing next to
 * each {@code x.flac} its {@code x.rc} (the exit code of {@code flac -t}), {@code x.raw} (the samples
 * {@code flac -d} decoded) and {@code x.fp} ({@code fpcalc}'s compressed fingerprint). It is a tool for
 * finding gaps, not a check that runs with the suite: it needs {@code FLAC_FUZZ_DIR}, a folder holding a
 * {@code corpus} folder of FLACs with their references. {@code compareCorpus} checks them unchanged, bit-exact
 * against {@code flac} and by fingerprint against {@code fpcalc}. {@code writeMutants} (with
 * {@code FLAC_FUZZ_SEEDS}, how many damaged copies of each to make) writes them to the {@code mutants} folder, and
 * once {@code reference.sh} has run over it {@code compareMutants} checks each: Sona may refuse a damaged file with
 * one of its documented exceptions, but anything else it throws, or a hang, or samples that differ from
 * {@code flac}'s while it accepts the file, is a bug.
 */
@EnabledIfEnvironmentVariable(named = "FLAC_FUZZ_DIR", matches = ".+")
class FlacFuzzComparisonTest {

    private static final int TIMEOUT_SECONDS = 120;
    // Larger files make mutants whose references take long to decode, and add nothing a smaller one doesn't.
    private static final long MAX_MUTATED_BYTES = 8_000_000;
    // Under this many bit errors in a thousand, a fingerprint is the same recording to AcoustID.
    private static final int NEARLY_EQUAL_PER_MILLE = 10;

    private enum Mutation {
        CUT_HEAD,
        CUT_TAIL,
        CUT_IN_METADATA,
        JUNK,
        GARBAGE_HEAD,
        ZEROS,
        FLIPS,
        FLIPS_IN_METADATA,
        SET_BYTES_IN_METADATA,
        DUPLICATE_SEGMENT,
        SWAP_SEGMENTS
    }

    @Test
    void compareCorpus() throws IOException {
        final var lines = new ArrayList<String>();
        try (final var files = Files.list(dir().resolve("corpus"))) {
            for (final var file :
                    files.filter(f -> f.toString().endsWith(".flac")).sorted().toList()) {
                lines.add(compareClean(file));
            }
        }
        report("corpus-report.txt", lines);
    }

    @Test
    void writeMutants() throws IOException {
        final var seeds = Integer.parseInt(System.getenv().getOrDefault("FLAC_FUZZ_SEEDS", "4"));
        final var mutants = dir().resolve("mutants");
        Files.createDirectories(mutants);
        try (final var files = Files.list(dir().resolve("corpus"))) {
            final var sources = files.filter(
                            f -> f.toString().endsWith(".flac") && f.toFile().length() < MAX_MUTATED_BYTES)
                    .sorted()
                    .toList();
            for (var seed = 0; seed < seeds; seed++) {
                for (var index = 0; index < sources.size(); index++) {
                    final var random = new Random(seed * 100_003L + index);
                    final var mutation = Mutation.values()[(seed + index) % Mutation.values().length];
                    final var bytes = mutate(Files.readAllBytes(sources.get(index)), mutation, random);
                    final var name = "%s-s%d-%s".formatted(stem(sources.get(index)), seed, mutation);
                    Files.write(mutants.resolve(name + ".flac"), bytes);
                }
            }
        }
    }

    @Test
    void compareMutants() throws IOException {
        final var lines = new ArrayList<String>();
        try (final var files = Files.list(dir().resolve("mutants"))) {
            for (final var file :
                    files.filter(f -> f.toString().endsWith(".flac")).sorted().toList()) {
                lines.add(compareMutant(file));
            }
        }
        report("mutant-report.txt", lines);
    }

    private static Path dir() {
        return Path.of(System.getenv("FLAC_FUZZ_DIR"));
    }

    private static void report(final String name, final List<String> lines) throws IOException {
        Files.write(dir().resolve(name), lines);
        System.out.println("wrote " + dir().resolve(name));
    }

    private static String stem(final Path file) {
        final var name = file.getFileName().toString();
        return name.substring(0, name.length() - ".flac".length());
    }

    private static Path sibling(final Path file, final String extension) {
        return file.resolveSibling(stem(file) + extension);
    }

    // ---- the corpus, unchanged ----

    private static String compareClean(final Path file) throws IOException {
        return "%s\t%s\t%s".formatted(stem(file), decode(file), fingerprintVerdict(file, true));
    }

    // ---- the mutants ----

    private static String compareMutant(final Path file) throws IOException {
        final var rc = Integer.parseInt(Files.readString(sibling(file, ".rc")).trim());
        final var decode = decode(file);
        final var fingerprint = fingerprintVerdict(file, rc == 0);
        final String verdict;
        if (decode.startsWith("BUG") || decode.startsWith("HANG") || decode.startsWith("DIFFERS")) {
            verdict = decode;
        } else if (decode.startsWith("REJECTED") && rc == 0) {
            verdict = "REJECTED_VALID " + decode;
        } else if (decode.startsWith("OK") && rc != 0) {
            verdict = "ACCEPTED_BAD(flac rc=%d) %s".formatted(rc, decode);
        } else {
            verdict = decode;
        }
        return "%s\t%s\t%s\t%s".formatted(stem(file), verdict, fingerprint, originalVerdict(file));
    }

    /**
     * A mutant that only changes metadata has the samples of the file it came from, so if Sona accepts it, it must
     * fingerprint as that file does, whatever {@code flac} makes of it.
     */
    private static String originalVerdict(final Path file) throws IOException {
        final var name = stem(file);
        if (!name.endsWith("FLIPS_IN_METADATA") && !name.endsWith("SET_BYTES_IN_METADATA")) {
            return "ORIG_NOT_APPLICABLE";
        }
        final var original = dir().resolve("corpus").resolve(name.substring(0, name.indexOf("-s")) + ".fp");
        final var expected = Files.readString(original).trim();
        final var bytes = Files.readAllBytes(file);
        final var ours = runWithTimeout(() -> {
            try {
                final var parsed = new FlacParser().parse(new SeekableInMemoryByteChannel(bytes));
                return new Fingerprint(parsed.audio()).compressed();
            } catch (final IOException | InvalidFormatException | RuntimeException e) {
                return "REJECTED";
            }
        });
        if (ours.equals("REJECTED") || expected.isEmpty()) {
            return "ORIG_NOT_APPLICABLE";
        }
        if (ours.equals(expected)) {
            return "ORIG_SAME";
        }
        return "ORIG_DIFFERS %d per mille bit errors".formatted(bitErrorsPerMille(ours, expected));
    }

    // ---- Sona against flac ----

    /**
     * Decodes the whole file and compares its samples with those {@code flac} decoded, as little-endian signed
     * integers at the stream's own width.
     */
    private static String decode(final Path file) throws IOException {
        final var bytes = Files.readAllBytes(file);
        final var raw = Files.readAllBytes(sibling(file, ".raw"));
        return runWithTimeout(() -> decodeAgainst(bytes, raw));
    }

    private static String decodeAgainst(final byte[] bytes, final byte[] raw) throws Exception {
        final var parsed = new FlacParser().parse(new SeekableInMemoryByteChannel(bytes));
        final var width = (parsed.audio().bitsPerSample() + 7) / 8;
        var offset = 0L;
        var mismatches = 0L;
        var first = -1L;
        try {
            for (final PcmFrame frame : parsed.audio()) {
                for (var index = 0; index < frame.sampleCount(); index++) {
                    for (var channel = 0; channel < frame.channelCount(); channel++) {
                        final var actual = frame.channelSample(channel, index);
                        if (offset + width > raw.length) {
                            return "LONGER than flac by at least %d bytes".formatted(offset + width - raw.length);
                        }
                        var expected = 0;
                        for (var b = width - 1; b >= 0; b--) {
                            expected = expected << 8 | raw[(int) offset + b] & 0xFF;
                        }
                        expected = expected << (32 - 8 * width) >> (32 - 8 * width);
                        if (expected != actual && first < 0) {
                            first = offset / width;
                        }
                        if (expected != actual) {
                            mismatches++;
                        }
                        offset += width;
                    }
                }
            }
        } catch (final RuntimeException e) {
            return refusal(e, mismatches, first);
        }
        if (mismatches > 0) {
            return "DIFFERS %d samples, first at %d".formatted(mismatches, first);
        }
        if (offset != raw.length) {
            return "SHORTER than flac by %d bytes (flac wrote %d)".formatted(raw.length - offset, raw.length);
        }
        return "OK %d bytes".formatted(offset);
    }

    private static String refusal(final RuntimeException e, final long mismatches, final long first) {
        final var documented = e instanceof UncheckedInvalidFormatException
                || e instanceof UncheckedIOException
                || e.getCause() instanceof IOException;
        if (!documented) {
            return "BUG " + e;
        }
        if (mismatches > 0) {
            return "REJECTED %s; DIFFERS %d samples before it, first at %d"
                    .formatted(e.getClass().getSimpleName(), mismatches, first);
        }
        return "REJECTED " + e.getClass().getSimpleName();
    }

    // ---- Sona's fingerprint against fpcalc ----

    private static String fingerprintVerdict(final Path file, final boolean referenceWasValid) throws IOException {
        final var bytes = Files.readAllBytes(file);
        final var reference = Files.readString(sibling(file, ".fp")).trim();
        final String ours;
        try {
            ours = runWithTimeout(() -> {
                try {
                    final var parsed = new FlacParser().parse(new SeekableInMemoryByteChannel(bytes));
                    return new Fingerprint(parsed.audio()).compressed();
                } catch (final IOException | InvalidFormatException | UncheckedInvalidFormatException e) {
                    return "REJECTED " + e.getClass().getSimpleName();
                } catch (final RuntimeException e) {
                    if (e.getCause() instanceof IOException) {
                        return "REJECTED " + e;
                    }
                    return "BUG " + e;
                }
            });
        } catch (final RuntimeException e) {
            return "FP_BUG " + e;
        }
        if (ours.startsWith("REJECTED")) {
            if (referenceWasValid) {
                return "FP_REJECTED_VALID " + ours;
            }
            return "FP_" + ours;
        }
        if (ours.startsWith("BUG") || ours.startsWith("HANG")) {
            return "FP_" + ours;
        }
        if (!referenceWasValid) {
            return "FP_UNCHECKED";
        }
        if (reference.isEmpty()) {
            return "FP_NO_REFERENCE (ours %d chars)".formatted(ours.length());
        }
        if (ours.equals(reference)) {
            return "FP_EXACT";
        }
        final var errors = bitErrorsPerMille(ours, reference);
        if (errors <= NEARLY_EQUAL_PER_MILLE) {
            return "FP_NEAR %d per mille bit errors".formatted(errors);
        }
        return "FP_DIFFERS %d per mille bit errors".formatted(errors);
    }

    private static long bitErrorsPerMille(final String ours, final String reference) {
        final var ourItems = decompress(ours);
        final var theirItems = decompress(reference);
        final var common = Math.min(ourItems.length, theirItems.length);
        var differing = 0L;
        for (var index = 0; index < common; index++) {
            differing += Integer.bitCount(ourItems[index] ^ theirItems[index]);
        }
        // Items one side has and the other lacks count as wholly wrong.
        differing += 32L * Math.abs(ourItems.length - theirItems.length);
        final var total = 32L * Math.max(Math.max(ourItems.length, theirItems.length), 1);
        return differing * 1000 / total;
    }

    /** Chromaprint's compressed form: a header, then the gaps between each item's set bits, 3 bits each. */
    private static int[] decompress(final String compressed) {
        final var data = Base64.getUrlDecoder().decode(compressed.getBytes(StandardCharsets.US_ASCII));
        final var size = (data[1] & 0xFF) << 16 | (data[2] & 0xFF) << 8 | data[3] & 0xFF;
        var exceptionPosition = endOfNormalSymbols(data, size);
        var normalPosition = 4 * 8L;
        final var items = new int[size];
        var bit = 0;
        var value = 0;
        var last = 0;
        for (var item = 0; item < size; ) {
            final var symbol = (int) readBits(data, normalPosition, 3);
            normalPosition += 3;
            if (symbol == 0) {
                last ^= value;
                items[item++] = last;
                value = 0;
                bit = 0;
            } else {
                var gap = symbol;
                if (symbol == 7) {
                    gap += (int) readBits(data, exceptionPosition, 5);
                    exceptionPosition += 5;
                }
                bit += gap;
                value |= 1 << (bit - 1);
            }
        }
        return items;
    }

    // The bit position after the normal symbols, which are padded to a byte and followed by the exceptions.
    private static long endOfNormalSymbols(final byte[] data, final int size) {
        var position = 4 * 8L;
        for (var zeros = 0; zeros < size; position += 3) {
            if (readBits(data, position, 3) == 0) {
                zeros++;
            }
        }
        return (position + 7) / 8 * 8;
    }

    private static long readBits(final byte[] data, final long from, final int count) {
        var result = 0L;
        for (var i = 0; i < count; i++) {
            final var position = from + i;
            if (position / 8 < data.length && (data[(int) (position / 8)] >> (position % 8) & 1) != 0) {
                result |= 1L << i;
            }
        }
        return result;
    }

    // ---- mutation ----

    private static String runWithTimeout(final Callable<String> task) {
        final var executor = Executors.newSingleThreadExecutor(runnable -> {
            final var thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });
        try {
            return executor.submit(task).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (final TimeoutException e) {
            return "HANG after %d s".formatted(TIMEOUT_SECONDS);
        } catch (final ExecutionException e) {
            final var cause = e.getCause();
            if (cause instanceof IOException || cause instanceof InvalidFormatException) {
                return "REJECTED " + cause.getClass().getSimpleName() + ": " + cause.getMessage();
            }
            return "BUG " + cause;
        } catch (final InterruptedException e) {
            throw new IllegalStateException(e);
        } finally {
            executor.shutdownNow();
        }
    }

    private static byte[] mutate(final byte[] bytes, final Mutation mutation, final Random random) {
        final var at = random.nextInt(Math.max(bytes.length, 1));
        final var header = metadataEnd(bytes);
        return switch (mutation) {
            case CUT_HEAD -> Arrays.copyOfRange(bytes, at, bytes.length);
            case CUT_TAIL -> Arrays.copyOf(bytes, at);
            case CUT_IN_METADATA -> Arrays.copyOf(bytes, random.nextInt(Math.max(header, 1)));
            case JUNK -> insert(bytes, at, randomBytes(random, 1 + random.nextInt(2000)));
            case GARBAGE_HEAD -> insert(bytes, 0, randomBytes(random, 1 + random.nextInt(5000)));
            case ZEROS -> insert(bytes, at, new byte[1 + random.nextInt(3000)]);
            case FLIPS -> flipBits(bytes, bytes.length, random);
            case FLIPS_IN_METADATA -> flipBits(bytes, header, random);
            case SET_BYTES_IN_METADATA -> setBytes(bytes, header, random);
            case DUPLICATE_SEGMENT -> {
                final var length = Math.min(random.nextInt(5000) + 1, bytes.length - at);
                yield insert(bytes, at, Arrays.copyOfRange(bytes, at, at + length));
            }
            case SWAP_SEGMENTS -> {
                final var result = bytes.clone();
                final var length = Math.min(random.nextInt(3000) + 1, bytes.length / 2);
                final var other = random.nextInt(Math.max(bytes.length - length, 1));
                final var from = Math.min(at, bytes.length - length);
                System.arraycopy(bytes, other, result, from, length);
                System.arraycopy(bytes, from, result, other, length);
                yield result;
            }
        };
    }

    // Where the metadata blocks end, found by walking their headers up to the one marked last.
    private static int metadataEnd(final byte[] bytes) {
        var offset = 4;
        while (offset + 4 <= bytes.length) {
            final var size =
                    (bytes[offset + 1] & 0xFF) << 16 | (bytes[offset + 2] & 0xFF) << 8 | bytes[offset + 3] & 0xFF;
            final var last = (bytes[offset] & 0x80) != 0;
            offset += 4 + size;
            if (last) {
                return Math.min(offset, bytes.length);
            }
        }
        return Math.min(bytes.length, 300);
    }

    private static byte[] randomBytes(final Random random, final int count) {
        final var junk = new byte[count];
        random.nextBytes(junk);
        return junk;
    }

    private static byte[] insert(final byte[] bytes, final int at, final byte[] inserted) {
        return Stream.of(Arrays.copyOf(bytes, at), inserted, Arrays.copyOfRange(bytes, at, bytes.length))
                .reduce(new byte[0], (a, b) -> {
                    final var joined = Arrays.copyOf(a, a.length + b.length);
                    System.arraycopy(b, 0, joined, a.length, b.length);
                    return joined;
                });
    }

    private static byte[] flipBits(final byte[] bytes, final int within, final Random random) {
        final var result = bytes.clone();
        for (var flips = 1 + random.nextInt(20); flips > 0 && within > 0; flips--) {
            result[random.nextInt(within)] ^= (byte) (1 << random.nextInt(Byte.SIZE));
        }
        return result;
    }

    private static byte[] setBytes(final byte[] bytes, final int within, final Random random) {
        final var result = bytes.clone();
        for (var sets = 1 + random.nextInt(4); sets > 0 && within > 0; sets--) {
            result[random.nextInt(within)] = (byte) random.nextInt(256);
        }
        return result;
    }
}
