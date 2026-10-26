package com.smartopd.api.security;

import java.lang.annotation.*;

/**
 * RequiresRole - Declarative access control annotation for REST controllers and endpoints.
 * 
 * ============================================================================
 * OOP & Architectural Concepts:
 * - Declarative Security: Endpoints specify necessary authorization roles without
 *   cluttering business logic with role checks.
 * - Enforced centrally by AuthInterceptor (the Spring WebMvc equivalent of a Servlet Filter).
 * ============================================================================
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresRole {
    String[] value();
}
