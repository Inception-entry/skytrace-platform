package com.skytrace.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class ApiSecurityConfig {

    @Bean
    @ConditionalOnProperty(
            name = "app.security.enabled",
            havingValue = "false",
            matchIfMissing = true
    )
    public SecurityFilterChain permitAllFilterChain(
            HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )
                .build();
    }

    @Bean
    @ConditionalOnProperty(
            name = "app.security.enabled",
            havingValue = "true"
    )
    public SecurityFilterChain jwtFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            ApiSecurityErrorHandler errorHandler) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS
                ))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/health").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/knowledge/search"
                        ).hasAnyRole("ADMIN", "OPERATOR", "VIEWER")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/knowledge/documents"
                        ).hasRole("ADMIN")
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/knowledge/documents/**"
                        ).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/**")
                        .hasAnyRole("ADMIN", "OPERATOR", "VIEWER")
                        .requestMatchers(HttpMethod.HEAD, "/**")
                        .hasAnyRole("ADMIN", "OPERATOR", "VIEWER")
                        .anyRequest().hasAnyRole("ADMIN", "OPERATOR")
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler)
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(
                                        jwtAuthenticationConverter
                                )
                        )
                )
                .build();
    }

    @Bean
    @ConditionalOnProperty(
            name = "app.security.enabled",
            havingValue = "true"
    )
    public JwtAuthenticationConverter jwtAuthenticationConverter(
            @Value("${app.security.client-id:skytrace-web}")
            String clientId) {
        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                new KeycloakRoleConverter(clientId)
        );
        return converter;
    }

    @Bean
    @ConditionalOnProperty(
            name = "app.security.enabled",
            havingValue = "true"
    )
    public JwtDecoder jwtDecoder(
            @Value("${app.security.jwk-set-uri}")
            String jwkSetUri,
            @Value("${app.security.issuer-uri}")
            String issuerUri,
            @Value("${app.security.audience}")
            String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(jwkSetUri)
                .build();
        OAuth2TokenValidator<Jwt> issuerValidator =
                JwtValidators.createDefaultWithIssuer(issuerUri);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                audienceValidator(audience)
        ));
        return decoder;
    }

    private OAuth2TokenValidator<Jwt> audienceValidator(
            String audience) {
        return jwt -> jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token",
                        "访问令牌 audience 无效",
                        null
                ));
    }
}
