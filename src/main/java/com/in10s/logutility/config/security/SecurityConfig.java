package com.in10s.logutility.config.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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
 * Application security, split across two filter chains:
 * <ul>
 *   <li>{@link #apiSecurityFilterChain} — the JSON REST API ({@code /api/**}). Search endpoints
 *       are public; project-config endpoints require the single admin account, sent as HTTP
 *       Basic on every request (stateless — no session, no CSRF, so any client — browser UI,
 *       curl, another backend — can call it just by sending an {@code Authorization} header).</li>
 *   <li>{@link #appSecurityFilterChain} — the server-rendered Thymeleaf/HTMX UI. Unchanged:
 *       {@code /admin}, {@code /config}, and the wizard/upload endpoints require the admin's
 *       session-based form login; searching ({@code /}, {@code /search}) is public. Sessions are
 *       persisted via Spring Session JDBC (auto-configured) so a logged-in admin stays logged in
 *       across app instances.</li>
 * </ul>
 * Both chains share the one admin account below.
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

    /**
     * The REST API. Scoped to {@code /api/**} via {@link HttpSecurity#securityMatcher}, so it
     * never sees requests the UI chain below handles, and vice versa. Stateless: no session is
     * created and CSRF is disabled, since HTTP Basic requires an explicit {@code Authorization}
     * header on every request rather than a cookie the browser attaches automatically — the
     * cross-site request forgery CSRF protection guards against doesn't apply here.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/search/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain appSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Public: search UI + login + static assets + health + API docs.
                        .requestMatchers("/", "/search/**").permitAll()
                        .requestMatchers("/login", "/error").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/webjars/**", "/favicon.ico").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Everything else — admin console, wizard/upload — is protected.
                        .requestMatchers("/admin/**", "/config/**").authenticated()
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
