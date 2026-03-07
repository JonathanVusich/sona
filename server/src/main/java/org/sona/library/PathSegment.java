package org.sona.library;

import java.nio.file.Path;
import java.util.UUID;

public record PathSegment(
        UUID id,
        String name
) {
    public Path path() {
        return Path.of(toString());
    }

    public static PathSegment parse(String path) {
        final var uuid = UUID.fromString(path.substring(0, 32));
        final var name = path.substring(1, path.length() - 1);
        return new PathSegment(uuid, name);
    }

    @Override
    public String toString() {
        return "%s[%s]".formatted(id, name);
    }
}
