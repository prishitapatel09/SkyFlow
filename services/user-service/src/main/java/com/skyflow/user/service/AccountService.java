package com.skyflow.user.service;

import com.skyflow.common.exception.ConflictException;
import com.skyflow.common.exception.ResourceNotFoundException;
import com.skyflow.common.exception.UnauthorizedException;
import com.skyflow.common.security.JwtService;
import com.skyflow.user.domain.UserAccount;
import com.skyflow.user.dto.AuthResponse;
import com.skyflow.user.dto.ChangePasswordRequest;
import com.skyflow.user.dto.LoginRequest;
import com.skyflow.user.dto.RegisterRequest;
import com.skyflow.user.dto.UpdateProfileRequest;
import com.skyflow.user.dto.UserDto;
import com.skyflow.user.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Account lifecycle and token issuance. The Express codebase had a React client calling
 * {@code /auth/*} endpoints that were never implemented server side; this is that missing half.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final UserAccountRepository users;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AccountService(UserAccountRepository users, JwtService jwtService) {
        this.users = users;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account already exists for " + email);
        }
        UserAccount account = new UserAccount();
        account.setName(request.name().trim());
        account.setEmail(email);
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setRole("user");

        UserAccount saved = users.save(account);
        log.info("Registered account {}", saved.getId());
        return tokensFor(saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        UserAccount account = users.findByEmailIgnoreCase(request.email().trim())
                // Same message for unknown email and wrong password, so the endpoint is not an
                // account-existence oracle.
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        return tokensFor(account);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        String userId = jwtService.subjectOfRefreshToken(refreshToken);
        UserAccount account = users.findById(Long.valueOf(userId))
                .orElseThrow(() -> new UnauthorizedException("Account no longer exists"));
        return tokensFor(account);
    }

    @Transactional(readOnly = true)
    public UserDto get(Long id) {
        return toDto(users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id)));
    }

    @Transactional(readOnly = true)
    public List<UserDto> findAll() {
        return users.findAll().stream().map(AccountService::toDto).toList();
    }

    @Transactional
    public UserDto updateProfile(Long id, UpdateProfileRequest request) {
        UserAccount account = users.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        if (request.name() != null && !request.name().isBlank()) {
            account.setName(request.name().trim());
        }
        if (request.email() != null && !request.email().isBlank()) {
            String email = request.email().trim().toLowerCase();
            if (!email.equals(account.getEmail()) && users.existsByEmailIgnoreCase(email)) {
                throw new ConflictException("An account already exists for " + email);
            }
            account.setEmail(email);
        }
        if (request.phone() != null) {
            account.setPhone(request.phone());
        }
        return toDto(users.save(account));
    }

    @Transactional
    public void changePassword(Long id, ChangePasswordRequest request) {
        UserAccount account = users.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new UnauthorizedException("Current password is incorrect");
        }
        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        users.save(account);
        log.info("Password changed for account {}", id);
    }

    private AuthResponse tokensFor(UserAccount account) {
        String id = String.valueOf(account.getId());
        return new AuthResponse(
                jwtService.issueAccessToken(id, account.getEmail(), account.getRole()),
                jwtService.issueRefreshToken(id),
                jwtService.accessTokenTtlSeconds(),
                toDto(account));
    }

    private static UserDto toDto(UserAccount account) {
        return new UserDto(account.getId(), account.getName(), account.getEmail(), account.getRole(),
                account.getPhone(), account.getCreatedAt(), account.getUpdatedAt());
    }
}
