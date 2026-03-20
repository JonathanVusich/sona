package org.sona.client.model;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum Format {
    XML("xml"),
    JSON("json");

    private final String value;
}
