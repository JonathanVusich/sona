package org.sona.client.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum EntityType {
    ANNOTATION("annotation"),
    AREA("area"),
    ARTIST("artist"),
    CDSTUB("cdstub"),
    EVENT("event"),
    INSTRUMENT("instrument"),
    LABEL("label"),
    PLACE("place"),
    RECORDING("recording"),
    RELEASE("release"),
    RELEASE_GROUP("release-group"),
    SERIES("series"),
    TAG("tag"),
    WORK("work"),
    URL("url");

    private final String path;
}
