package org.sona.format.flac;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.sona.metadata.Tag;
import org.sona.metadata.TagValue;

import java.time.Year;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Getter
public enum FlacTag {
    ARTIST("artist", Tag.TRACK_ARTIST),
    ALBUM_ARTIST("albumartist", Tag.ALBUM_ARTIST),
    TITLE("title", Tag.TRACK_TITLE),
    ALBUM("album", Tag.RELEASE_TITLE),
    TRACK_NUMBER("tracknumber", Tag.TRACK_NUMBER),
    DISC_NUMBER("discnumber", Tag.DISC_NUMBER),
    ORIGINAL_YEAR("originalyear", Tag.RELEASE_YEAR),
    COMPOSER("composer", Tag.COMPOSER);

    private static final Map<String, FlacTag> TAGS = Arrays.stream(FlacTag.values())
            .collect(Collectors.toUnmodifiableMap(FlacTag::getFieldName, Function.identity()));

    private final String fieldName;
    private final Tag tag;

    public TagValue convert(String fieldValue) {
        return switch (this) {
            case ALBUM_ARTIST, TITLE, ALBUM, ARTIST, COMPOSER -> new TagValue.Str(fieldValue);
            case TRACK_NUMBER, DISC_NUMBER -> new TagValue.Int(Integer.parseInt(fieldValue));
            case ORIGINAL_YEAR -> new TagValue.Int(Year.parse(fieldValue).getValue());
        };
    }

    public static FlacTag from(String fieldName) {
        return TAGS.get(fieldName);
    }
}
