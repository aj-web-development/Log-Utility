package com.in10s.logutility.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

/**
 * Application security. Configuration ({@code /admin}, {@code /config}, project write APIs and
 * the wizard/upload endpoints) requires the single admin login; searching ({@code /},
 * {@code /search}, {@code /api/search}) is public. Sessions are persisted via Spring Session
 * JDBC (auto-configured) so a logged-in admin stays logged in across app instances.
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
    @Order(2)
    public SecurityFilterChain appSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Public: search UI + read APIs + login + static assets + health.
                        .requestMatchers("/", "/search/**", "/api/search/**").permitAll()
                        .requestMatchers("/login", "/error").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/webjars/**", "/favicon.ico").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // Everything else — admin console, project write APIs, wizard/upload — is protected.
                        .requestMatchers("/admin/**", "/config/**", "/api/projects/**").authenticated()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/admin", true)
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/?logout")
                        .permitAll());
        return http.build();
    }
}
