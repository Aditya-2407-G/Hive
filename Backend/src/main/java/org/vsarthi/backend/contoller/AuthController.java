package org.vsarthi.backend.contoller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vsarthi.backend.model.Users;
import org.vsarthi.backend.service.UserService;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    @Autowired
    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Users user) {
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            return ResponseEntity.badRequest().body("Password cannot be null or empty");
        }
        Users registeredUser = userService.register(user);
        return ResponseEntity.ok(registeredUser);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Users user, HttpServletResponse response) {
        UserService.TokenPair tokens = userService.authenticateAndGenerateTokens(user.getEmail(), user.getPassword());
        Users fullUserDetails = userService.getUserDetails(user.getEmail());

        // Set access token in a cookie
        ResponseCookie jwtCookie = ResponseCookie.from("accessToken", tokens.accessToken)
                .httpOnly(true)
                .secure(true) // Enable in production
                .sameSite("Strict")
                .domain("hive-two-lake.vercel.app")
                .path("/")
                .maxAge(3600) // 1 hour
                .build();

        // Set refresh token in a separate, secure cookie
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", tokens.refreshToken)
                .httpOnly(true)
                .secure(true) // Enable in production
                .path("/") // not the best practice, but for simplicity
                .sameSite("Strict")
                .domain("hive-two-lake.vercel.app")
                .maxAge(604800) // 1 week
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(Map.of(
                        "message", "Login successful",
                        "user", fullUserDetails,
                        "tokens", tokens
                ));
    }

    @GetMapping("/user")
    public ResponseEntity<?> getUserDetails(@RequestParam String email) {
        Users user = userService.getUserDetails(email);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        request.getSession().invalidate();
        UserService.removeCookies(response);

        // Clear the access token and refresh token cookies
        ResponseCookie clearJwtCookie = ResponseCookie.from("accessToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .sameSite("Strict")
                .domain("hive-two-lake.vercel.app")
                .maxAge(0)
                .build();

        ResponseCookie clearRefreshCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .sameSite("Strict")
                .domain("hive-two-lake.vercel.app")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearJwtCookie.toString())
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie.toString())
                .body("Logged out successfully");
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@CookieValue(name = "refreshToken", required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            return ResponseEntity.badRequest().body("Refresh token is missing");
        }

        try {
            UserService.TokenPair newTokens = userService.refreshTokens(refreshToken);

            ResponseCookie newJwtCookie = ResponseCookie.from("accessToken", newTokens.accessToken)
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .sameSite("Strict")
                    .domain("hive-two-lake.vercel.app")
                    .maxAge(3600)
                    .build();

            ResponseCookie newRefreshCookie = ResponseCookie.from("refreshToken", newTokens.refreshToken)
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .sameSite("Strict")
                    .domain("hive-two-lake.vercel.app")
                    .maxAge(604800)
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, newJwtCookie.toString())
                    .header(HttpHeaders.SET_COOKIE, newRefreshCookie.toString())
                    .body(Map.of(
                            "message", "Token refreshed successfully",
                            "accessToken", newTokens.accessToken,
                            "refreshToken", newTokens.refreshToken
                    ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Invalid refresh token");
        }
    }
}