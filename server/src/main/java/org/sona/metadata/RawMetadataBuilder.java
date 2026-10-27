package org.sona.metadata;

import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Collects the tags a parser reads, to make a {@link RawMetadata}.
 */
public final class RawMetadataBuilder {

    private final Duration duration;
    private final Map<Tag, Set<TagValue>> definedTags = new EnumMap<>(Tag.class);
    private final Map<String, Set<TagValue>> unknownTags = new HashMap<>();

    public RawMetadataBuilder(final Duration duration) {
        this.duration = duration;
    }

    public void addTag(final Tag tag, final TagValue value) {
        definedTags.computeIfAbsent(tag, _ -> new LinkedHashSet<>()).add(value);
    }

    public void addUnknownTag(final String tag, final String value) {
        unknownTags.computeIfAbsent(tag, _ -> new LinkedHashSet<>()).add(new TagValue.Str(value));
    }

    public RawMetadata build() {
        return new RawMetadata(duration, definedTags, unknownTags);
    }
}
