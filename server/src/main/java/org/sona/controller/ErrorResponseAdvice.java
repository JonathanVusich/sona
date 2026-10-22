package org.sona.controller;

import static org.sona.utils.HttpUtils.discardRequestBody;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.sona.controller.response.ErrorResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Reads the rest of a refused request's body before {@link ApiExceptionHandler}'s answer is written, as an upload can
 * be refused before its body is read (see {@code HttpUtils.discardRequestBody}).
 */
@RestControllerAdvice
public final class ErrorResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(
            final MethodParameter returnType, final Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            final Object body,
            final MethodParameter returnType,
            final MediaType selectedContentType,
            final Class<? extends HttpMessageConverter<?>> selectedConverterType,
            final ServerHttpRequest request,
            final ServerHttpResponse response) {
        if (body instanceof ErrorResponse && request instanceof ServletServerHttpRequest servletRequest) {
            discardBody(servletRequest);
        }
        return body;
    }

    private static void discardBody(final ServletServerHttpRequest request) {
        try {
            discardRequestBody(request.getServletRequest());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
