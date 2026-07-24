package com.app.logutility.config.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

/**
 * The whole app is now one stateless chain: {@code /api/projects/**} requires the single admin
 * account over HTTP Basic on every request; everything else — the public search/project JSON API
 * plus the static React SPA (and its client-routed paths, forwarded to {@code index.html} by
 * {@link com.app.logutility.controller.web.SpaController}) — is open. There is no more
 * server-rendered admin UI to session-gate: the SPA shell loads for anyone, and its own JS decides
 * what to show based on whether an API call with stored credentials succeeds. Real enforcement
 * stays exactly where the data actually changes — the {@code /api/projects/**} endpoints below.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AdminProperties.class)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * The one admin user, held in memory with its password BCrypt-encoded at build time.
     * Fails fast if credentials are missing so a production instance can never start with an
     * unauthenticated or blank admin account.
     */
    @Bean
    public UserDetailsService userDetailsService(AdminProperties admin, PasswordEncoder encoder) {
        if (!StringUtils.hasText(admin.getUsername()) || !StringUtils.hasText(admin.getPassword())) {
            throw new IllegalStateException(
                    "Admin credentials are not configured. Set the LOGUTY_ADMIN_USERNAME and "
                            + "LOGUTY_ADMIN_PASSWORD environment variables.");
        }
        UserDetails adminUser = User.withUsername(admin.getUsername())
                .password(encoder.encode(admin.getPassword()))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(adminUser);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/projects/**").authenticated()
                        .anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults());
        return http.build();
    }
}
