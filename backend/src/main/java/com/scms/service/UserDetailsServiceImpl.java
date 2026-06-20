package com.scms.service;

import com.scms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UserDetailsServiceImpl — tells Spring Security how to load a user by email.
 *
 * MENTOR NOTE:
 * Spring Security calls loadUserByUsername() internally during authentication.
 * Our "username" is the email address. We look it up in the DB and return the
 * User object, which implements UserDetails, so Spring Security can then:
 *   - extract the password hash and verify it with BCrypt
 *   - extract roles/authorities for authorization checks
 *   - check isEnabled(), isAccountNonLocked(), etc.
 *
 * @Transactional(readOnly=true) keeps the Hibernate session alive so that
 * the EAGER-fetched roles Set is populated before the session closes.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No user found with email: " + email));
    }
}
