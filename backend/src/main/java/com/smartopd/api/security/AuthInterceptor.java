package com.smartopd.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

/**
 * AuthInterceptor - Central HTTP request interceptor verifying Bearer tokens and roles.
 * 
 * ============================================================================
 * OOP & Viva Concept: Interceptor Pattern (same architectural idea as a Servlet Filter)
 * - Intercepts execution before reaching the Controller handler.
 * - Inspects the 'Authorization: Bearer <token>' HTTP header.
 * - Validates cryptographic session token against the in-memory SessionStore.
 * - Enforces @RequiresRole annotations and doctor-specific queue ownership.
 * - Emits standardized JSON responses on 401 Unauthorized or 403 Forbidden.
 * ============================================================================
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final SessionStore sessionStore;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(SessionStore sessionStore, ObjectMapper objectMapper) {
        this.sessionStore = sessionStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true; // Static resource or non-method handler
        }

        String path = request.getRequestURI();

        // 1. Resolve @RequiresRole annotation on method or class level
        RequiresRole roleAnnotation = handlerMethod.getMethodAnnotation(RequiresRole.class);
        if (roleAnnotation == null) {
            roleAnnotation = handlerMethod.getBeanType().getAnnotation(RequiresRole.class);
        }

        // Check if endpoint is under protected /api path (excluding /api/public, /api/auth/login, and allowed setup endpoints)
        String method = request.getMethod();
        boolean isSetupPublic = (path.equals("/api/setup/status") && "GET".equalsIgnoreCase(method))
                             || (path.equals("/api/setup") && "POST".equalsIgnoreCase(method));
        boolean isPublicPath = path.startsWith("/api/public/") || path.equals("/api/auth/login") || isSetupPublic;
        boolean isProtected = roleAnnotation != null || (path.startsWith("/api/") && !isPublicPath);

        if (!isProtected) {
            return true;
        }

        // 2. Extract Bearer token from Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                    "Missing or invalid Authorization header. Expected 'Bearer <token>'.", path);
            return false;
        }

        String token = authHeader.substring(7).trim();
        Optional<UserSession> sessionOpt = sessionStore.getSession(token);
        if (sessionOpt.isEmpty()) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                    "Session token is invalid or has expired. Please log in again.", path);
            return false;
        }

        UserSession session = sessionOpt.get();
        request.setAttribute("currentUser", session);

        // 3. Verify Role Authorization
        if (roleAnnotation != null) {
            Set<String> allowedRoles = new HashSet<>(Arrays.asList(roleAnnotation.value()));
            if (!allowedRoles.contains(session.role())) {
                sendError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                        "User role '" + session.role() + "' is not authorized for this resource. Required role(s): " + allowedRoles, path);
                return false;
            }
        }

        // 4. Verify Doctor Ownership: DOCTOR role can only operate on their own doctorId
        if ("DOCTOR".equalsIgnoreCase(session.role())) {
            // Check URI path variables (e.g., /api/doctors/{id}/...)
            @SuppressWarnings("unchecked")
            Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            if (pathVariables != null && pathVariables.containsKey("id")) {
                String targetDoctorId = pathVariables.get("id");
                if (targetDoctorId != null && !targetDoctorId.equalsIgnoreCase(session.doctorId())) {
                    sendError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                            "Doctor is only authorized to access and manage their own doctor queue (Assigned ID: " + session.doctorId() + ").", path);
                    return false;
                }
            }

            // Check doctorId query parameter if present for /api/events
            if (path.startsWith("/api/events")) {
                String doctorIdParam = request.getParameter("doctorId");
                if (doctorIdParam == null || !doctorIdParam.equalsIgnoreCase(session.doctorId())) {
                    sendError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                            "Doctor is only authorized to query events for their own doctor ID (" + session.doctorId() + ").", path);
                    return false;
                }
            }
        }

        return true;
    }

    private void sendError(HttpServletResponse response, int status, String error, String message, String path) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("status", status);
        errorBody.put("error", error);
        errorBody.put("message", message);
        errorBody.put("path", path);
        errorBody.put("timestamp", Instant.now().toString());
        response.getWriter().write(objectMapper.writeValueAsString(errorBody));
    }
}
