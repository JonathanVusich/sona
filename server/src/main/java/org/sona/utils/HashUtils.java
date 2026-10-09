package org.sona.utils;

import lombok.experimental.UtilityClass;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@UtilityClass
public final class HashUtils {

    /**
     * @return the SHA-256 hash of the text's UTF-8 bytes, hex encoded
     */
    public static String sha256Hex(final String text) {
        try {
            final var digest = MessageDigest.getInstance("SHA-256");
            final var hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (final NoSuchAlgorithmException e) {
            // Every Java platform must support SHA-256.
            throw new IllegalStateException(e);
        }
    }
}
