package org.sona.metadata;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record TagSet(String tag, Set<TagValue> values) {

    public TagSet {
        values = Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
