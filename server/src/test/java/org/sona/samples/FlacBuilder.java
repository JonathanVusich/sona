package org.sona.samples;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds tiny synthetic FLAC files: a STREAMINFO block, an optional VORBIS_COMMENT block and a few bytes of
 * placeholder "audio". Good enough for the metadata parser and for byte-for-byte file handling tests.
 */
public final class FlacBuilder {

    private static final int STREAM_INFO = 0;
    private static final int VORBIS_COMMENT = 4;

    private final List<String> comments = new ArrayList<>();
    private static final int SAMPLE_RATE = 44_100;
    private static final long TOTAL_SAMPLES = 441_000; // 10 seconds

    private boolean streamInfo = true;
    private boolean vorbisComment = true;

    public static FlacBuilder flac() {
        return new FlacBuilder();
    }

    public FlacBuilder tag(final String name, final String value) {
        comments.add(name + "=" + value);
        return this;
    }

    public FlacBuilder withoutStreamInfo() {
        this.streamInfo = false;
        return this;
    }

    public FlacBuilder withoutVorbisComment() {
        this.vorbisComment = false;
        return this;
    }

    public byte[] build() {
        final var out = new ByteArrayOutputStream();
        out.writeBytes("fLaC".getBytes(StandardCharsets.US_ASCII));

        if (streamInfo) {
            out.writeBytes(block(STREAM_INFO, !vorbisComment, streamInfoBody()));
        }
        if (vorbisComment) {
            out.writeBytes(block(VORBIS_COMMENT, true, vorbisCommentBody()));
        }

        // Placeholder audio frames so file copies/moves can be checked byte for byte.
        for (int i = 0; i < 256; i++) {
            out.write(i);
        }
        return out.toByteArray();
    }

    public InputStream inputStream() {
        return new ByteArrayInputStream(build());
    }

    private static byte[] block(final int type, final boolean last, final byte[] body) {
        final var header = ByteBuffer.allocate(4 + body.length);
        header.put((byte) ((last ? 0x80 : 0) | type));
        header.put((byte) (body.length >>> 16));
        header.put((byte) (body.length >>> 8));
        header.put((byte) body.length);
        header.put(body);
        return header.array();
    }

    private byte[] streamInfoBody() {
        final var body = ByteBuffer.allocate(34);
        body.putShort((short) 4096); // min block size
        body.putShort((short) 4096); // max block size
        body.put(new byte[3]);       // min frame size (unknown)
        body.put(new byte[3]);       // max frame size (unknown)

        final int channels = 2;
        final int bitsPerSample = 16;
        final long packed = ((long) SAMPLE_RATE << 44)
                | ((long) (channels - 1) << 41)
                | ((long) (bitsPerSample - 1) << 36)
                | TOTAL_SAMPLES;
        body.putLong(packed);
        body.put(new byte[16]);      // MD5 of the audio (unknown)
        return body.array();
    }

    private byte[] vorbisCommentBody() {
        final var vendor = "sona-test".getBytes(StandardCharsets.UTF_8);
        final var encoded = comments.stream()
                .map(comment -> comment.getBytes(StandardCharsets.UTF_8))
                .toList();
        final int size = 4 + vendor.length + 4 + encoded.stream().mapToInt(c -> 4 + c.length).sum();

        // Vorbis comment lengths are little endian.
        final var body = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        body.putInt(vendor.length).put(vendor);
        body.putInt(encoded.size());
        for (final var comment : encoded) {
            body.putInt(comment.length).put(comment);
        }
        return body.array();
    }
}
