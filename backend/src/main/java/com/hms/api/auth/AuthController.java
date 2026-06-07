package com.hms.api.auth;
import com.hms.api.shared.ApiResponse;
import com.hms.security.HmsUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Set;
@RestController @RequestMapping("/auth") @RequiredArgsConstructor
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final com.hms.infrastructure.settings.SettingsRegistryImpl settingsRegistry;

    @PostMapping({"/login", "/session"})
    public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody LoginRequest req, HttpServletRequest request) {
        Authentication auth = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        SecurityContextHolder.getContext().setAuthentication(auth);
        HttpSession session = request.getSession(true);
        
        // Set session timeout from configuration (convert minutes to seconds)
        // Add a 3-minute grace period (180s) so the backend doesn't expire before the frontend's 1-minute timeout warning has a chance to be answered.
        session.setMaxInactiveInterval((settingsRegistry.getSessionTimeoutMinutes() * 60) + 180);

        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
            SecurityContextHolder.getContext());
        HmsUserDetails user = (HmsUserDetails) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.ok("Login successful",
            new LoginResponse(user.getId(), user.getUsername(), user.getFeatureKeys(), user.isSuperAdmin())));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<LoginResponse>> me() {
        HmsUserDetails user = (HmsUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(ApiResponse.ok("OK",
            new LoginResponse(user.getId(), user.getUsername(), user.getFeatureKeys(), user.isSuperAdmin())));
    }

    @GetMapping("/heartbeat")
    public ResponseEntity<ApiResponse<Void>> heartbeat(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setMaxInactiveInterval((settingsRegistry.getSessionTimeoutMinutes() * 60) + 180);
        }
        return ResponseEntity.ok(ApiResponse.ok("Session refreshed"));
    }

    public record LoginRequest(String username, String password) {}
    public record LoginResponse(java.util.UUID id, String username, Set<String> featureKeys, boolean isSuperAdmin) {}
}
