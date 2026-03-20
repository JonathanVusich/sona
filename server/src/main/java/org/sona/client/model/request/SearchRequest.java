package org.sona.client.model.request;

import lombok.Builder;
import lombok.Value;
import org.sona.client.model.EntityType;
import org.sona.client.model.Format;

@Builder
@Value
public class SearchRequest {
    EntityType type;
    String query;

    @Builder.Default
    Format fmt = Format.JSON;
    @Builder.Default
    int limit = 10;
    @Builder.Default
    int offset = 0;
    @Builder.Default
    boolean dismax = false;
    @Builder.Default
    int version = 2;
}
