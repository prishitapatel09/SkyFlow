package com.skyflow.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the caller as an {@link AuthenticatedUser} controller argument.
 *
 * <p>Annotate with {@code @CurrentUser(required = false)} for endpoints that also serve anonymous
 * traffic - the argument is then {@code null} when no identity headers are present.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {

    boolean required() default true;
}
