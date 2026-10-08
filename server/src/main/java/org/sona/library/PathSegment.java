package org.sona.library;

import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A library directory or file name of the form {@code <uuid>[<name>]}. Characters that aren't allowed in file
 * names on Windows, macOS or Linux (such as the '/' in "AC/DC") are replaced in the name.
 */
public record PathSegment(
        UUID id,
        String name
) {
    private static final int UUID_LENGTH = 36;
    private static final Pattern UNSAFE_CHARACTERS = Pattern.compile("[<>:\"/\\\\|?*\\p{Cntrl}]");

    public PathSegment {
        name = UNSAFE_CHARACTERS.matcher(name).replaceAll("_");
    }

    public Path path() {
        return Path.of(toString());
    }

    public static PathSegment parse(String path) {
        final var uuid = UUID.fromString(path.substring(0, UUID_LENGTH));
        final var name = path.substring(UUID_LENGTH + 1, path.length() - 1);
        return new PathSegment(uuid, name);
    }

    @Override
    public String toString() {
        return "%s[%s]".formatted(id, name);
    }
}
