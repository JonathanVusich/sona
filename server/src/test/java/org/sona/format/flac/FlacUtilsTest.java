package org.sona.format.flac;

import org.junit.jupiter.api.Test;
import org.sona.metadata.Blob;
import org.sona.metadata.TagValue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlacUtilsTest {

    @Test
    void toFieldsWritesOneUpperCaseFieldPerValue() {
        final var values = new LinkedHashSet<TagValue>(List.of(new TagValue.Str("Coldplay"), new TagValue.Int(9)));

        final var fields = FlacUtils.toFields("artist", values).toList();

        assertThat(fields).containsExactly(new Field("ARTIST", "Coldplay"), new Field("ARTIST", "9"));
    }

    @Test
    void toFieldsRefusesBinaryValues() {
        final var values = Set.<TagValue>of(new TagValue.Binary(Blob.copyOf(new byte[]{1})));

        assertThatThrownBy(() -> FlacUtils.toFields("picture", values).toList())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
