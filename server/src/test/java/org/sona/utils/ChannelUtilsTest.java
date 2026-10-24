package org.sona.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.EOFException;
import java.io.IOException;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.junit.jupiter.api.Test;

class ChannelUtilsTest {

    @Test
    void readsBytesAtPosition() throws IOException {
        final var channel = new SeekableInMemoryByteChannel(new byte[] {1, 2, 3, 4, 5, 6});

        final var bytes = ChannelUtils.readBytes(channel, 2, 3);

        assertThat(bytes.remaining()).isEqualTo(3);
        assertThat(bytes.array()).containsExactly(3, 4, 5);
        assertThat(channel.position()).isEqualTo(5);
    }

    @Test
    void throwsWhenChannelEndsFirst() {
        final var channel = new SeekableInMemoryByteChannel(new byte[] {1, 2, 3});

        assertThatThrownBy(() -> ChannelUtils.readBytes(channel, 1, 3)).isInstanceOf(EOFException.class);
    }
}
