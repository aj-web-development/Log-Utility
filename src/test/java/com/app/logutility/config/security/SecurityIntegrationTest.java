package com.app.logutility.config.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the public/protected split and the single-admin form login (dev creds admin/admin) for
 * the Thymeleaf UI's session-based chain, plus the separate stateless HTTP Basic chain for
 * {@code /api/**} (see {@link SecurityConfig#apiSecurityFilterChain}) — an anonymous request there
 * gets a bare 401, not a redirect to {@code /login}, since there's no session to redirect into.
 * MockMvc is built with {@code springSecurity()} so the Spring Security filter chain and the
 * {@code @WithMockUser} context are applied to each request.
 */
@SpringBootTest
class SecurityIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void homeIsPublic() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void loginPageIsPublic() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
    }

    @Test
    void adminRedirectsToLoginWhenAnonymous() throws Exception {
        mvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl())
                        .isNotNull()
                        .endsWith("/login"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminIsReachableWhenAuthenticated() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().isOk());
    }

    @Test
    void formLoginSucceedsWithConfiguredCredentials() throws Exception {
        mvc.perform(formLogin().user("admin").password("admin"))
                .andExpect(authenticated().withUsername("admin"))
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    void formLoginFailsWithWrongPassword() throws Exception {
        mvc.perform(formLogin().user("admin").password("nope"))
                .andExpect(unauthenticated());
    }

    @Test
    void apiSearchIsPublic() throws Exception {
        mvc.perform(get("/api/search/projects")).andExpect(status().isOk());
    }

    @Test
    void apiProjectsRejectsAnonymousWithoutRedirecting() throws Exception {
        // Unlike /admin, this must be a bare 401 — there's no session to redirect into.
        mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
    }

    @Test
    void apiProjectsAcceptsHttpBasicWithConfiguredCredentials() throws Exception {
        mvc.perform(get("/api/projects").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk());
    }

    @Test
    void apiProjectsRejectsHttpBasicWithWrongPassword() throws Exception {
        mvc.perform(get("/api/projects").with(httpBasic("admin", "wrong")))
                .andExpect(status().isUnauthorized());
    }
}
