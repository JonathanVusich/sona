package org.sona.metadata;

public sealed interface TagValue permits TagValue.Str, TagValue.Int, TagValue.Binary {

    record Str(String value) implements TagValue {}
    record Int(int value) implements TagValue {}
    record Binary(Blob blob) implements TagValue {}
}
