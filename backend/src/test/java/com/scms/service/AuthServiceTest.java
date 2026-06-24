package com.scms.service;

import com.scms.dto.AuthDtos.LoginRequest;
import com.scms.dto.AuthDtos.RegisterRequest;
import com.scms.model.Role;
import com.scms.model.User;
import com.scms.repository.RoleRepository;
import com.scms.repository.UserRepository;
import com.scms.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AuthServiceTest — unit tests for registration, login, and the two-layer
 * brute-force protection (IP throttle + account lockout) described in
 * LoginAttemptService's javadoc.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private AuthenticationManager authManager;
    @Mock private LoginAttemptService loginAttemptService;

    private AuthService authService;

    private Role userRole;
    private User existingUser;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, roleRepository, passwordEncoder, jwtUtil, authManager, loginAttemptService);

        userRole = Role.builder().id(1).name("USER").build();
        existingUser = User.builder()
                .id(1L)
                .firstName("Ali").lastName("Hassan")
                .email("ali@scms.com")
                .password("hashed")
                .active(true)
                .tokenVersion(0)
                .roles(Set.of(userRole))
                .build();
    }

    @Test
    void register_createsUserWithUserRole_andIssuesTokenPair() {
        RegisterRequest req = new RegisterRequest("Ali", "Hassan", "ALI@scms.com", "Password1", "9999999999");

        when(userRepository.existsByEmail("ali@scms.com")).thenReturn(false);
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode("Password1")).thenReturn("hashed-pw");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(42L);
            return u;
        });
        when(jwtUtil.generateAccessToken(eq("ali@scms.com"), eq(0))).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(eq("ali@scms.com"), eq(0))).thenReturn("refresh-token");
        when(jwtUtil.getAccessExpirationMs()).thenReturn(900_000L);
        when(jwtUtil.getRefreshExpirationMs()).thenReturn(604_800_000L);

        AuthService.AuthResult result = authService.register(req);

        assertEquals("access-token", result.getBody().getAccessToken());
        assertEquals("refresh-token", result.getRefreshToken());
        assertEquals("ali@scms.com", result.getBody().getEmail());
        assertTrue(result.getBody().getRoles().contains("USER"));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_duplicateEmail_throwsIllegalArgumentException() {
        RegisterRequest req = new RegisterRequest("Ali", "Hassan", "ali@scms.com", "Password1", null);
        when(userRepository.existsByEmail("ali@scms.com")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authService.register(req));
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_ipThrottled_rejectsBeforeCheckingCredentials() {
        LoginRequest req = new LoginRequest("ali@scms.com", "Password1");
        when(loginAttemptService.isIpThrottled(any())).thenReturn(true);

        assertThrows(AuthService.TooManyAttemptsException.class, () -> authService.login(req));
        verify(authManager, never()).authenticate(any());
    }

    @Test
    void login_lockedAccount_throwsTooManyAttempts() {
        LoginRequest req = new LoginRequest("ali@scms.com", "Password1");
        when(loginAttemptService.isIpThrottled(any())).thenReturn(false);
        when(authManager.authenticate(any())).thenThrow(new LockedException("locked"));

        assertThrows(AuthService.TooManyAttemptsException.class, () -> authService.login(req));
    }

    @Test
    void login_badCredentials_recordsFailureAndReportsRemainingAttempts() {
        LoginRequest req = new LoginRequest("ali@scms.com", "wrong-password");
        when(loginAttemptService.isIpThrottled(any())).thenReturn(false);
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("bad creds"));
        when(userRepository.findByEmail("ali@scms.com")).thenReturn(Optional.of(existingUser));
        when(loginAttemptService.remainingAttempts(existingUser)).thenReturn(3);

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> authService.login(req));
        assertTrue(ex.getMessage().contains("3 attempt"));
        verify(loginAttemptService).recordFailure(existingUser);
    }

    @Test
    void login_badCredentials_zeroRemaining_throwsTooManyAttempts() {
        LoginRequest req = new LoginRequest("ali@scms.com", "wrong-password");
        when(loginAttemptService.isIpThrottled(any())).thenReturn(false);
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("bad creds"));
        when(userRepository.findByEmail("ali@scms.com")).thenReturn(Optional.of(existingUser));
        when(loginAttemptService.remainingAttempts(existingUser)).thenReturn(0);

        assertThrows(AuthService.TooManyAttemptsException.class, () -> authService.login(req));
    }

    @Test
    void login_success_recordsSuccessAndIssuesTokens() {
        LoginRequest req = new LoginRequest("ali@scms.com", "Password1");
        Authentication auth = new UsernamePasswordAuthenticationToken(existingUser, null);

        when(loginAttemptService.isIpThrottled(any())).thenReturn(false);
        when(authManager.authenticate(any())).thenReturn(auth);
        when(jwtUtil.generateAccessToken(eq("ali@scms.com"), eq(0))).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(eq("ali@scms.com"), eq(0))).thenReturn("refresh-token");
        when(jwtUtil.getAccessExpirationMs()).thenReturn(900_000L);
        when(jwtUtil.getRefreshExpirationMs()).thenReturn(604_800_000L);

        AuthService.AuthResult result = authService.login(req);

        assertEquals("access-token", result.getBody().getAccessToken());
        verify(loginAttemptService).recordSuccess(existingUser);
    }
}
