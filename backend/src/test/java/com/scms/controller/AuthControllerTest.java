package com.scms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scms.dto.AuthDtos.AuthResponse;
import com.scms.service.AuthService;
import com.scms.service.AuthService.AuthResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthControllerTest — @WebMvcTest slice covering request validation and
 * the refresh-token cookie behaviour, without needing a full Spring context
 * or database. Security filters are disabled here (addFilters = false)
 * since /api/auth/** is permitAll anyway — this test is about the
 * controller's HTTP contract, not the security filter chain itself.
 */
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private AuthService authService;

    @Test
    void register_withInvalidEmail_returns400WithFieldErrors() throws Exception {
        String body = """
                {"firstName":"Ali","lastName":"Hassan","email":"not-an-email","password":"Password1"}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").exists());
    }

    @Test
    void register_withWeakPassword_returns400() throws Exception {
        String body = """
                {"firstName":"Ali","lastName":"Hassan","email":"ali@scms.com","password":"short"}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_success_returnsAccessTokenAndSetsRefreshCookie() throws Exception {
        AuthResponse response = AuthResponse.builder()
                .accessToken("access-token")
                .expiresInSeconds(900)
                .userId(1L)
                .firstName("Ali").lastName("Hassan")
                .email("ali@scms.com")
                .roles(Set.of("USER"))
                .build();
        AuthResult result = AuthResult.builder()
                .body(response)
                .refreshToken("refresh-token-value")
                .refreshExpiresInSeconds(604800)
                .build();

        when(authService.login(any())).thenReturn(result);

        String body = """
                {"email":"ali@scms.com","password":"Password1"}
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.roles[0]").value("USER"))
                .andExpect(cookie().exists("scms_refresh"))
                .andExpect(cookie().httpOnly("scms_refresh", true));
    }
}
