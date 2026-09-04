package com.skyflow.user.web;

import com.skyflow.common.api.ApiResponse;
import com.skyflow.common.security.AuthenticatedUser;
import com.skyflow.common.security.CurrentUser;
import com.skyflow.user.dto.AuthResponse;
import com.skyflow.user.dto.ChangePasswordRequest;
import com.skyflow.user.dto.LoginRequest;
import com.skyflow.user.dto.RefreshRequest;
import com.skyflow.user.dto.RegisterRequest;
import com.skyflow.user.dto.UpdateProfileRequest;
import com.skyflow.user.dto.UserDto;
import com.skyflow.user.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AccountService accountService;

    public AuthController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(accountService.register(request), "Registration successful");
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(accountService.login(request), "Login successful");
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.success(accountService.refresh(request.refreshToken()), "Token refreshed");
    }

    /**
     * Tokens are stateless, so there is nothing to revoke server side; the client drops them. The
     * endpoint exists so the React client's logout call has somewhere to land.
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.success(null, "Logged out");
    }

    @GetMapping("/me")
    public ApiResponse<UserDto> me(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.success(accountService.get(Long.valueOf(user.id())), "OK");
    }

    @PutMapping("/profile")
    public ApiResponse<UserDto> updateProfile(@CurrentUser AuthenticatedUser user,
                                              @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(accountService.updateProfile(Long.valueOf(user.id()), request),
                "Profile updated");
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@CurrentUser AuthenticatedUser user,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        accountService.changePassword(Long.valueOf(user.id()), request);
        return ApiResponse.success(null, "Password changed");
    }
}
