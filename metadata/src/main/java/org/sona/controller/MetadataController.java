package org.sona.controller;

import org.sona.controller.request.SearchRequest;
import org.sona.controller.response.SearchResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class MetadataController {

    @GetMapping("/search")
    public SearchResponse searchForTrack(SearchRequest request) {
        return new SearchResponse();
    }
}
