package org.sona.format.mp3;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.sona.format.PcmFrame;

/**
 * Decodes a corpus of MP3s with Sona and with FFmpeg and writes where they differ. It is a tool for finding gaps, not a
 * check that runs with the suite: it needs {@code MP3_CORPUS_LIST} (a file listing one MP3 per line), {@code FFMPEG}
 * (the ffmpeg executable), {@code MP3_CORPUS_REPORT} (where to write the report) and {@code MP3_CORPUS_WORK} (a folder
 * for the files it makes, where it keeps those that differ). With {@code MP3_CORPUS_MUTATE} set to a seed, each file is
 * first damaged, as a seeded random choice of cuts, junk, zeroed runs and flipped bytes. Each file is cut to its first
 * 6 MB, so one that is longer ends in a frame the cut shortens, which FFmpeg reads past the end of, so the two may
 * differ in its last samples.
 */
@EnabledIfEnvironmentVariable(named = "MP3_CORPUS_LIST", matches = ".+")
class Mp3CorpusComparisonTest {

    // ISO/IEC 11172-4's tolerance.
    private static final int TOLERANCE = 1;
    private static final int MAX_BYTES = 6_000_000;
    private static final int NO_SAMPLE = Integer.MIN_VALUE;

    private enum Mutation {
        NONE,
        CUT_HEAD,
        CUT_TAIL,
        JUNK,
        GARBAGE_HEAD,
        ZEROS,
        FLIPS
    }

    private record Damaged(byte[] bytes, Mutation mutation) {}

    @Test
    void comparesCorpusWithFfmpeg() throws IOException, InterruptedException {
        final var files = Files.readAllLines(Path.of(System.getenv("MP3_CORPUS_LIST")), StandardCharsets.UTF_8);
        final var report = Path.of(System.getenv("MP3_CORPUS_REPORT"));
        final var work = Path.of(System.getenv("MP3_CORPUS_WORK"));
        Files.createDirectories(work);
        Files.writeString(report, "");
        for (var index = 0; index < files.size(); index++) {
            final var source = Path.of(files.get(index));
            if (Files.exists(source)) {
                compareFile(source, index, report, work);
            }
        }
    }

    private void compareFile(final Path source, final int index, final Path report, final Path work)
            throws IOException, InterruptedException {
        final var damaged = damage(readPrefix(source), index);
        final var file = work.resolve("current.mp3");
        Files.write(file, damaged.bytes());
        final var result = compare(file);
        final var line = "%s\t%s\t%s%n".formatted(result, damaged.mutation(), source);
        Files.writeString(report, line, StandardOpenOption.APPEND);
        if (!result.startsWith("OK")) {
            Files.write(work.resolve("fail-%d.mp3".formatted(index)), damaged.bytes());
        }
    }

    private static Damaged damage(final byte[] bytes, final int index) {
        final var seed = System.getenv("MP3_CORPUS_MUTATE");
        if (seed == null) {
            return new Damaged(bytes, Mutation.NONE);
        }
        final var random = new Random(Long.parseLong(seed) * 100_003 + index);
        final var mutations = Arrays.copyOfRange(Mutation.values(), 1, Mutation.values().length);
        final var mutation = mutations[random.nextInt(mutations.length)];
        return new Damaged(applyMutation(bytes, mutation, random), mutation);
    }

    private static byte[] readPrefix(final Path path) throws IOException {
        try (final var channel = FileChannel.open(path)) {
            final var buffer = ByteBuffer.allocate((int) Math.min(channel.size(), MAX_BYTES));
            channel.read(buffer);
            return buffer.array();
        }
    }

    private static byte[] applyMutation(final byte[] bytes, final Mutation mutation, final Random random) {
        final var at = random.nextInt(Math.max(bytes.length, 1));
        return switch (mutation) {
            case NONE -> bytes;
            case CUT_HEAD -> Arrays.copyOfRange(bytes, at, bytes.length);
            case CUT_TAIL -> Arrays.copyOf(bytes, at);
            case JUNK -> insert(bytes, at, randomBytes(random, 1 + random.nextInt(2000)));
            case GARBAGE_HEAD -> insert(bytes, 0, randomBytes(random, 1 + random.nextInt(5000)));
            case ZEROS -> insert(bytes, at, new byte[1 + random.nextInt(3000)]);
            case FLIPS -> flipBits(bytes, random);
        };
    }

    private static byte[] randomBytes(final Random random, final int count) {
        final var junk = new byte[count];
        random.nextBytes(junk);
        return junk;
    }

    private static byte[] insert(final byte[] bytes, final int at, final byte[] inserted) {
        final var result = new byte[bytes.length + inserted.length];
        System.arraycopy(bytes, 0, result, 0, at);
        System.arraycopy(inserted, 0, result, at, inserted.length);
        System.arraycopy(bytes, at, result, at + inserted.length, bytes.length - at);
        return result;
    }

    private static byte[] flipBits(final byte[] bytes, final Random random) {
        final var result = bytes.clone();
        for (var flips = 1 + random.nextInt(20); flips > 0 && result.length > 0; flips--) {
            result[random.nextInt(result.length)] ^= (byte) (1 << random.nextInt(Byte.SIZE));
        }
        return result;
    }

    private static String compare(final Path file) throws IOException, InterruptedException {
        final var process = new ProcessBuilder(
                        System.getenv("FFMPEG"), "-v", "error", "-i", file.toString(), "-f", "s16le", "-")
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        try (final var reference = new BufferedInputStream(process.getInputStream(), 1 << 16)) {
            return compareWith(file, reference);
        } finally {
            process.destroy();
            process.waitFor();
        }
    }

    // The samples of both decoders, with each frame's channels interleaved, as FFmpeg writes them.
    private static String compareWith(final Path file, final BufferedInputStream reference) throws IOException {
        var sampleIndex = 0L;
        var ourExtra = 0L;
        var mismatches = 0L;
        var firstMismatch = -1L;
        var firstValues = "";
        var largest = 0;
        var channels = -1;
        try (final var channel = FileChannel.open(file)) {
            for (final PcmFrame frame : new Mp3Parser().parse(channel).audio()) {
                if (channels != -1 && channels != frame.channelCount()) {
                    return "CHANNEL_CHANGE at sample " + sampleIndex;
                }
                channels = frame.channelCount();
                for (var index = 0; index < frame.sampleCount(); index++) {
                    for (var frameChannel = 0; frameChannel < channels; frameChannel++) {
                        final var expected = readSample(reference);
                        final var actual = frame.channelSample(frameChannel, index);
                        if (expected == NO_SAMPLE) {
                            ourExtra++;
                        } else if (Math.abs(expected - actual) > TOLERANCE) {
                            mismatches++;
                            largest = Math.max(largest, Math.abs(expected - actual));
                            if (firstMismatch == -1) {
                                firstMismatch = sampleIndex;
                                firstValues = "ours %d ffmpeg %d".formatted(actual, expected);
                            }
                        }
                        sampleIndex++;
                    }
                }
            }
        } catch (final Exception e) {
            return "THREW " + e;
        }
        var ffmpegExtra = 0L;
        while (readSample(reference) != NO_SAMPLE) {
            ffmpegExtra++;
        }
        if (mismatches > 0) {
            return "DIFFERS %d of %d samples, first at %d (%s), largest %d, ffmpeg has %d more"
                    .formatted(mismatches, sampleIndex, firstMismatch, firstValues, largest, ffmpegExtra);
        }
        if (ourExtra > 0) {
            return "LONGER by %d samples after %d".formatted(ourExtra, sampleIndex - ourExtra);
        }
        if (ffmpegExtra > 0) {
            return "SHORTER by %d samples after %d".formatted(ffmpegExtra, sampleIndex);
        }
        return "OK %d samples".formatted(sampleIndex);
    }

    // The next 16-bit little-endian sample, or NO_SAMPLE at the end.
    private static int readSample(final BufferedInputStream reference) throws IOException {
        final var low = reference.read();
        final var high = reference.read();
        if (high < 0) {
            return NO_SAMPLE;
        }
        return (short) (low | high << Byte.SIZE);
    }
}
