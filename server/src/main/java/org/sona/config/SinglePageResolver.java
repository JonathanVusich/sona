package org.sona.config;

import org.sona.controller.ApiRoutes;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Finds the web frontend's files, and answers every other path with its index.html, where the frontend's router takes
 * over. API paths are left alone, so an unknown endpoint is still a 404.
 */
public final class SinglePageResolver extends PathResourceResolver {

    private static final String INDEX = "index.html";

    @Override
    protected Resource getResource(final String resourcePath, final Resource location) throws IOException {
        // Resource paths come without the leading slash.
        if (resourcePath.startsWith(ApiRoutes.API.substring(1) + "/")) {
            return null;
        }
        final var resource = location.createRelative(resourcePath);
        if (resource.isReadable()) {
            return resource;
        }
        final var index = location.createRelative(INDEX);
        if (index.isReadable()) {
            return index;
        }
        // Without a bundled frontend, as under bootRun and most tests, there's nothing to serve.
        return null;
    }
}
