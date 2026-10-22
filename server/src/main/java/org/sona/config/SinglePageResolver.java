package org.sona.config;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Finds the web frontend's files, and answers every other path with its index.html, where the frontend's router takes
 * over.
 */
@RequiredArgsConstructor
public final class SinglePageResolver extends PathResourceResolver {

    private final Resource index;

    @Override
    protected Resource getResource(final String resourcePath, final Resource location) throws IOException {
        final var resource = location.createRelative(resourcePath);
        if (resource.isReadable()) {
            return resource;
        }
        return index;
    }
}
