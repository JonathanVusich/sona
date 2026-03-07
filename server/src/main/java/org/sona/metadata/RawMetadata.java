package org.sona.metadata;

import java.util.*;

public final class RawMetadata {

    private final Map<Tag, Set<TagValue>> definedTags = new EnumMap<>(Tag.class);
    private final Map<String, Set<TagValue>> unknownTags = new HashMap<>();

    public void add(Tag tag, String value) {
        definedTags.computeIfAbsent(tag, _ -> new LinkedHashSet<>()).add(new TagValue.Str(value));
    }

    public void add(Tag tag, int value) {
        definedTags.computeIfAbsent(tag, _ -> new LinkedHashSet<>()).add(new TagValue.Int(value));
    }

    public void add(Tag tag, Blob value) {
        definedTags.computeIfAbsent(tag, _ -> new LinkedHashSet<>()).add(new TagValue.Binary(value));
    }

    public void add(String tag, String value) {
        unknownTags.computeIfAbsent(tag, _ -> new LinkedHashSet<>()).add(new TagValue.Str(value));
    }

    public void add(String tag, int value) {
        unknownTags.computeIfAbsent(tag, _ -> new LinkedHashSet<>()).add(new TagValue.Int(value));
    }

    public void add(String tag, Blob value) {
        unknownTags.computeIfAbsent(tag, _ -> new LinkedHashSet<>()).add(new TagValue.Binary(value));
    }

    public Set<TagValue> search(Tag tag) {
        return definedTags.getOrDefault(tag, Set.of());
    }

    public Set<TagValue> search(String tag) {
        return unknownTags.getOrDefault(tag, Set.of());
    }
}
