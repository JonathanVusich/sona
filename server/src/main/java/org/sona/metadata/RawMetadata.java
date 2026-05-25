package org.sona.metadata;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class RawMetadata {

    private final Map<Tag, Set<TagValue>> definedTags = new EnumMap<>(Tag.class);
    private final Map<String, Set<TagValue>> unknownTags = new HashMap<>();

    public void add(Tag tag, TagValue value) {
        definedTags.computeIfAbsent(tag, _ -> new LinkedHashSet<>()).add(value);
    }

    public void add(String tag, String value) {
        unknownTags.computeIfAbsent(tag, _ -> new LinkedHashSet<>()).add(new TagValue.Str(value));
    }

    public Set<TagValue> search(Tag tag) {
        return definedTags.getOrDefault(tag, Set.of());
    }

    public Stream<TagSet> unknownTags() {
        return unknownTags.entrySet().stream()
                .map(entry -> new TagSet(entry.getKey(), entry.getValue()));
    }
}
