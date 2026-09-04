package com.skyflow.common.security;

import com.skyflow.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@code @CurrentUser AuthenticatedUser} parameters from the identity headers the
 * api-gateway injects after it verifies the JWT. Services are only reachable inside the cluster
 * network, so they trust those headers rather than re-verifying the token on every hop.
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && AuthenticatedUser.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        String id = request == null ? null : request.getHeader(AuthenticatedUser.HEADER_ID);

        if (id == null || id.isBlank()) {
            CurrentUser annotation = parameter.getParameterAnnotation(CurrentUser.class);
            if (annotation != null && annotation.required()) {
                throw new UnauthorizedException("Authentication required");
            }
            return null;
        }

        String role = request.getHeader(AuthenticatedUser.HEADER_ROLE);
        return new AuthenticatedUser(
                id,
                request.getHeader(AuthenticatedUser.HEADER_EMAIL),
                role == null ? "user" : role);
    }
}
