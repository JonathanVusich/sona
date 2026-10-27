package org.sona.metadata;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The tags and duration a parser read from a file, which never change once they're made. A {@link RawMetadataBuilder}
 * collects them as a parser reads them.
 */
public final class RawMetadata {

    private final Duration duration;
    private final Map<Tag, Set<TagValue>> definedTags;
    private final Map<String, Set<TagValue>> unknownTags;

    public RawMetadata(final Duration duration) {
        this(duration, Map.of(), Map.of());
    }

    /**
     * @param definedTags each known tag's values, in the order they were read, which the metadata copies
     * @param unknownTags each other tag's values, in the order they were read, which the metadata copies
     */
    public RawMetadata(
            final Duration duration,
            final Map<Tag, Set<TagValue>> definedTags,
            final Map<String, Set<TagValue>> unknownTags) {
        this.duration = duration;
        final var definedCopy = new EnumMap<Tag, Set<TagValue>>(Tag.class);
        definedTags.forEach((tag, values) -> definedCopy.put(tag, copiedValues(values)));
        this.definedTags = Collections.unmodifiableMap(definedCopy);
        final var unknownCopy = new LinkedHashMap<String, Set<TagValue>>();
        unknownTags.forEach((tag, values) -> unknownCopy.put(tag, copiedValues(values)));
        this.unknownTags = Collections.unmodifiableMap(unknownCopy);
    }

    public Duration duration() {
        return duration;
    }

    public Set<TagValue> findValues(final Tag tag) {
        return definedTags.getOrDefault(tag, Set.of());
    }

    public Stream<TagSet> unknownTags() {
        return unknownTags.entrySet().stream().map(entry -> new TagSet(entry.getKey(), entry.getValue()));
    }

    private static Set<TagValue> copiedValues(final Set<TagValue> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
