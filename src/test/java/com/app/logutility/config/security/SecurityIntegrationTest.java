package com.app.logutility.config.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The app is a single stateless chain: {@code /api/projects/**} requires the single admin account
 * over HTTP Basic on every request (dev creds admin/admin); everything else — including the SPA
 * shell paths served by {@link com.app.logutility.controller.web.SpaController} — is public, since
 * there's no more server-rendered content there to gate. An anonymous request to
 * {@code /api/projects/**} gets a bare 401, never a redirect (there's no session/login page to
 * redirect into). MockMvc is built with {@code springSecurity()} so the Spring Security filter
 * chain is applied to each request.
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
    void loginRouteIsPublic() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
    }

    @Test
    void adminRouteIsPublicShellAnonymously() throws Exception {
        // The SPA shell loads for anyone; its own JS decides what to render based on whether an
        // authenticated /api/projects/** call succeeds. Page-level access control is gone because
        // there's no server-rendered admin page left to protect.
        mvc.perform(get("/admin")).andExpect(status().isOk());
    }

    @Test
    void apiSearchIsPublic() throws Exception {
        mvc.perform(get("/api/search/projects")).andExpect(status().isOk());
    }

    @Test
    void apiProjectsRejectsAnonymousWithoutRedirecting() throws Exception {
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
