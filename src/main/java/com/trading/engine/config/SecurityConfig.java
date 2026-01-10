package com.trading.engine.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            final HttpSecurity http,
            final ApiKeyAuthenticationFilter apiKeyFilter) throws Exception {

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/webhook/health").permitAll()
                        .requestMatchers("/api/webhook/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Component
    @RequiredArgsConstructor
    public static class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

        @Value("${webhook.api.key:}")
        private String webhookApiKey;

        @Override
        protected void doFilterInternal(
                final HttpServletRequest request,
                final HttpServletResponse response,
                final FilterChain filterChain) throws ServletException, IOException {

            final var requestPath = request.getRequestURI();

            // Skip filter for health check endpoint
            if (requestPath.equals("/api/webhook/health")) {
                filterChain.doFilter(request, response);
                return;
            }

            // Only apply to /api/webhook/* endpoints (except health)
            if (requestPath.startsWith("/api/webhook/")) {

                // Check if API key is configured
                if (webhookApiKey == null || webhookApiKey.isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    response.getWriter().write("{\"error\":\"API key not configured\"}");
                    return;
                }

                // Get API key from header or query parameter
                var apiKey = request.getHeader("X-API-Key");
                if (apiKey == null || apiKey.isEmpty()) {
                    apiKey = request.getParameter("apiKey");
                }

                // Validate API key
                if (apiKey == null || !apiKey.equals(webhookApiKey)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Invalid or missing API key\"}");
                    return;
                }

                // API key is valid - set authentication in SecurityContext
                final var authorities = List.of(new SimpleGrantedAuthority("ROLE_API_USER"));
                final var authentication = new PreAuthenticatedAuthenticationToken(
                        "api-key-user", apiKey, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            filterChain.doFilter(request, response);
        }
    }
}
