package com.hms.api.user;
import com.hms.api.shared.ApiResponse;
import com.hms.api.user.request.*;
import com.hms.api.user.response.UserResponse;
import com.hms.application.user.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
@RestController @RequestMapping("/users") @RequiredArgsConstructor
public class UserController {
    private final UserManagementService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
        return ResponseEntity.ok(ApiResponse.ok("OK", userService.getCurrentUser()));
    }

    @GetMapping
    @PreAuthorize("hasPermission('SETTINGS_USERS', '')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("OK", userService.getAll()));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasPermission('SETTINGS_USERS', '')")
    public ResponseEntity<ApiResponse<UserResponse>> getById(@PathVariable("userId") UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", userService.getById(userId)));
    }

    @PostMapping
    @PreAuthorize("hasPermission('SETTINGS_USERS', '')")
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody CreateUserRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("User created successfully", userService.createUser(req)));
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasPermission('SETTINGS_USERS', '')")
    public ResponseEntity<ApiResponse<UserResponse>> update(
            @PathVariable("userId") UUID userId, @Valid @RequestBody UpdateUserRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("User updated successfully", userService.updateUser(userId, req)));
    }

    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        userService.changeOwnPassword(req);
        return ResponseEntity.ok(ApiResponse.ok("Password updated successfully"));
    }

    @PutMapping("/{userId}/password")
    @PreAuthorize("hasPermission('SETTINGS_USERS', '')")
    public ResponseEntity<ApiResponse<Void>> adminResetPassword(
            @PathVariable("userId") UUID userId, @RequestParam(name = "newPassword") String newPassword) {
        userService.adminResetPassword(userId, newPassword);
        return ResponseEntity.ok(ApiResponse.ok("Password reset successfully"));
    }

    @GetMapping("/check-password")
    public ResponseEntity<ApiResponse<Boolean>> checkPassword(@RequestParam(name = "currentPassword") String currentPassword) {
        return ResponseEntity.ok(ApiResponse.ok("OK", userService.checkCurrentPassword(currentPassword)));
    }

    /** GET /user/getCurrentConsultant — consultant linked to logged-in user */
    @GetMapping("/getCurrentConsultant")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getCurrentConsultant() {
        var user = userService.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok("OK",
            user.consultantId() != null
                ? java.util.Map.of("id", user.consultantId(), "userId", user.id())
                : java.util.Map.of()));
    }

    /** GET /user/getUserRole — roles of logged-in user */
    @GetMapping("/getUserRole")
    public ResponseEntity<ApiResponse<java.util.Set<String>>> getUserRole() {
        var user = userService.getCurrentUser();
        var roleNames = user.roles().stream()
            .map(com.hms.api.user.response.UserResponse.RoleSummary::name)
            .collect(java.util.stream.Collectors.toSet());
        return ResponseEntity.ok(ApiResponse.ok("OK", roleNames));
    }

    /** POST /user/updateUserConfig — user preferences */
    @PostMapping("/updateUserConfig")
    public ResponseEntity<ApiResponse<Void>> updateUserConfig(@RequestBody java.util.Map<String, Object> body) {
        var user = userService.getCurrentUser();
        var req = new com.hms.api.user.request.UpdateUserRequest(
            null, null, null, null, null, null, null, null,
            user.showCasesheet(),
            body.getOrDefault("speechLanguage", user.speechLanguage()).toString(),
            Boolean.parseBoolean(body.getOrDefault("textAutoSuggestion", user.textAutoSuggest()).toString()),
            null, null
        );
        userService.updateUser(user.id(), req);
        return ResponseEntity.ok(ApiResponse.ok("User config updated successfully"));
    }

    /**
     * GET /user/loggedInUser — returns the currently authenticated user's full profile.
     * Same as /user/me but at the SRS-specified URL.
     */
    @GetMapping("/loggedInUser")
    public ResponseEntity<ApiResponse<com.hms.api.user.response.UserResponse>> loggedInUser() {
        return ResponseEntity.ok(ApiResponse.ok("OK", userService.getCurrentUser()));
    }

    /**
     * PUT /user/updateLoginUserPassword?password= — logged-in user updates their own password.
     * Query-param style (not request body) — matches SRS §37.
     */
    @PutMapping("/updateLoginUserPassword")
    public ResponseEntity<ApiResponse<Void>> updateLoginUserPassword(
            @RequestParam(name = "password") String password) {
        var user = userService.getCurrentUser();
        userService.adminResetPassword(user.id(), password);
        return ResponseEntity.ok(ApiResponse.ok("User password updated successfully"));
    }

    /**
     * PUT /user/updatePassword?id=&password= — admin resets any user's password.
     * Query-param style. Requires SETTINGS_USERS permission.
     */
    @PutMapping("/updatePassword")
    @PreAuthorize("hasPermission('SETTINGS_USERS','')")
    public ResponseEntity<ApiResponse<Void>> updatePassword(
            @RequestParam java.util.UUID id,
            @RequestParam(name = "password") String password) {
        userService.adminResetPassword(id, password);
        return ResponseEntity.ok(ApiResponse.ok("User password reset successfully"));
    }

    

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<UserResponse>>> getPaginated(
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String value) {
        
        List<UserResponse> all = userService.getAll();
        
        if (value != null && !value.isBlank()) {
            String lowerValue = value.toLowerCase();
            all = all.stream().filter(e -> 
                (e.username() != null && e.username().toLowerCase().contains(lowerValue)) ||
                (e.firstName() != null && e.firstName().toLowerCase().contains(lowerValue)) ||
                (e.lastName() != null && e.lastName().toLowerCase().contains(lowerValue)) ||
                (e.fullName() != null && e.fullName().toLowerCase().contains(lowerValue)) ||
                (e.email() != null && e.email().toLowerCase().contains(lowerValue))
            ).toList();
        }
        
        int total = all.size();
        int startIndex = Math.min(start, total);
        int endIndex = Math.min(start + limit, total);
        List<UserResponse> pageContent = startIndex <= endIndex ? all.subList(startIndex, endIndex) : new java.util.ArrayList<>();
        
        org.springframework.data.domain.Page<UserResponse> page = new org.springframework.data.domain.PageImpl<>(
            pageContent, 
            org.springframework.data.domain.PageRequest.of(start / Math.max(limit, 1), Math.max(limit, 1)), 
            total
        );
        return ResponseEntity.ok(ApiResponse.ok("OK", page));
    }

}
