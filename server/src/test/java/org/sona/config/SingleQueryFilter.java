package org.sona.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.sona.controller.ApiRoutes;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Marks each read of Sona's API for {@link SingleQueryListener} to check. Signing in (under {@link ApiRoutes#AUTH})
 * writes as well as reads, though a provider sends the browser back to it with a GET, so it isn't checked.
 */
public final class SingleQueryFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        final var path = request.getRequestURI();
        final var read = request.getMethod().equals(HttpMethod.GET.name());
        return !read || !path.startsWith(ApiRoutes.API + "/") || path.startsWith(ApiRoutes.AUTH + "/");
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain chain) throws ServletException, IOException {
        SingleQueryListener.startRequest(request.getMethod() + " " + request.getRequestURI());
        try {
            chain.doFilter(request, response);
        } finally {
            SingleQueryListener.finishRequest();
        }
    }
}
