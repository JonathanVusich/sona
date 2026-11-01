package org.sona.library;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A library directory or file name of the form {@code <uuid>[<name>]}. Characters that aren't allowed in file
 * names on Windows, macOS or Linux (such as the '/' in "AC/DC") are replaced in the name.
 */
public record PathSegment(UUID id, String name) {
    private static final Pattern UNSAFE_CHARACTERS = Pattern.compile("[<>:\"/\\\\|?*\\p{Cntrl}]");

    public PathSegment {
        name = UNSAFE_CHARACTERS.matcher(name).replaceAll("_");
    }

    @Override
    public String toString() {
        return "%s[%s]".formatted(id, name);
    }
}
