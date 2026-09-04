package com.skyflow.user.web;

import com.skyflow.common.api.ApiResponse;
import com.skyflow.common.exception.UnauthorizedException;
import com.skyflow.common.security.AuthenticatedUser;
import com.skyflow.common.security.CurrentUser;
import com.skyflow.user.dto.UserDto;
import com.skyflow.user.service.AccountService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin views over accounts; the identity headers come from the gateway. */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final AccountService accountService;

    public UserController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ApiResponse<List<UserDto>> list(@CurrentUser AuthenticatedUser caller) {
        requireAdmin(caller);
        return ApiResponse.success(accountService.findAll(), "Successfully fetched users");
    }

    @GetMapping("/{id}")
    public ApiResponse<UserDto> get(@PathVariable Long id, @CurrentUser AuthenticatedUser caller) {
        if (!caller.isAdmin() && !caller.id().equals(String.valueOf(id))) {
            throw new UnauthorizedException("You may only read your own account");
        }
        return ApiResponse.success(accountService.get(id), "Successfully fetched the user");
    }

    private void requireAdmin(AuthenticatedUser caller) {
        if (!caller.isAdmin()) {
            throw new UnauthorizedException("Administrator role required");
        }
    }
}
