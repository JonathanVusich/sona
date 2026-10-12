package org.sona.format.flac;

import lombok.experimental.UtilityClass;
import org.sona.metadata.TagValue;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

@UtilityClass
public final class FlacUtils {

    /**
     * @return one Vorbis comment field per value, under the field name in upper case
     */
    public static Stream<Field> toFields(final String fieldName, final Set<TagValue> values) {
        final var name = fieldName.toUpperCase(Locale.ROOT);
        return values.stream()
                .map(value -> new Field(name, value.toText()));
    }
}
