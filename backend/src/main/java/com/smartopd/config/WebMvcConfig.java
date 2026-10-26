package com.smartopd.config;

import com.smartopd.api.security.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvcConfig - Configures Spring MVC interceptors and static resource hosting.
 * 
 * ============================================================================
 * OOP & Architectural Concepts:
 * - Single-Page Static File Serving: Serves the vanilla JS/HTML frontend directly
 *   from file:../frontend/ and file:frontend/ so http://localhost:8080/login.html
 *   and other pages work out-of-the-box.
 * - Central Interceptor Pipeline: Wires AuthInterceptor into the HTTP request chain.
 * ============================================================================
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/public/**"
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("file:../frontend/", "file:frontend/", "classpath:/static/");
    }
}
