package org.sona.utils;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class ChannelUtils {

    /**
     * Reads {@code length} bytes from {@code position}, leaving the channel just after them.
     *
     * @return the bytes read, big endian, ready to be read from the start
     * @throws EOFException when the channel ends before them
     */
    public static ByteBuffer readBytes(final SeekableByteChannel channel, final long position, final int length)
            throws IOException {
        final var buffer = ByteBuffer.allocate(length);
        channel.position(position);
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                throw new EOFException("Channel ends before the bytes being read!");
            }
        }
        return buffer.flip();
    }
}
