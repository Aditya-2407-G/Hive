package org.vsarthi.backend.controller;

import java.util.Collections;
import java.util.Map;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Autowired;
import java.security.GeneralSecurityException;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.vsarthi.backend.model.Users;
import org.vsarthi.backend.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;


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
                .path("/")
                .maxAge(3600) // 1 hour
                .build();

        // Set refresh token in a separate, secure cookie
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", tokens.refreshToken)
                .httpOnly(true)
                .secure(true) // Enable in production
                .path("/") // not the best practice, but for simplicity
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
                .maxAge(0)
                .build();

        ResponseCookie clearRefreshCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
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
                    .maxAge(3600)
                    .build();

            ResponseCookie newRefreshCookie = ResponseCookie.from("refreshToken", newTokens.refreshToken)
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
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

    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@RequestBody Map<String, String> body,
                                         HttpServletResponse response) {
        try {
            String idToken = body.get("idToken");

            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken googleIdToken = verifier.verify(idToken);
            if (googleIdToken == null) {
                return ResponseEntity.badRequest().body("Invalid ID token");
            }

            GoogleIdToken.Payload payload = googleIdToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");
            String pictureUrl = (String) payload.get("picture");

            // Get or create user
            Users user = userService.findOrCreateUser(payload.getSubject(), email, name);

            // Generate tokens
            UserService.TokenPair tokens = userService.generateTokens(user);

            // Set cookies
            ResponseCookie jwtCookie = ResponseCookie.from("accessToken", tokens.accessToken)
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .maxAge(3600)
                    .build();

            ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", tokens.refreshToken)
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .maxAge(604800)
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                    .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                    .body(Map.of(
                            "message", "Google login successful",
                            "user", user,
                            "tokens", tokens
                    ));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body("Google authentication failed: " + e.getMessage());
        }
    }


}