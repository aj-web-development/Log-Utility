package com.app.logutility.config.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The single admin account's credentials, supplied at runtime via the environment variables
 * {@code LOGUTY_ADMIN_USERNAME} / {@code LOGUTY_ADMIN_PASSWORD} (relaxed-bound to
 * {@code loguty.admin.username} / {@code loguty.admin.password}). The raw password is only ever
 * held in memory and BCrypt-encoded when the {@code UserDetailsService} is built — it is never
 * stored in the database. The dev profile supplies throwaway defaults in application-dev.yml.
 */
@ConfigurationProperties(prefix = "loguty.admin")
@Getter
@Setter
public class AdminProperties {

    private String username;

    private String password;
}
