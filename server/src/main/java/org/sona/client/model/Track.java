package org.sona.client.model;

import java.util.UUID;

public record Track(
        UUID id,
        String number,
        String title,
        Integer length
) {
}
