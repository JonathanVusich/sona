package org.sona.config;

import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Answers every path that isn't one of the frontend's files with index.html, where the frontend's router takes over.
 */
public final class SinglePageResolver extends PathResourceResolver {

    @Override
    protected Resource getResource(final String resourcePath, final Resource location) throws IOException {
        final var resource = location.createRelative(resourcePath);
        if (resource.isReadable()) {
            return resource;
        }
        return location.createRelative("index.html");
    }
}
