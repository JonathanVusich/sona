package org.sona.utils;

import lombok.experimental.UtilityClass;

import java.util.Collection;

@UtilityClass
public final class CollectionUtils {

    public boolean empty(Collection<?> collection) {
        return collection != null && collection.isEmpty();
    }
}
