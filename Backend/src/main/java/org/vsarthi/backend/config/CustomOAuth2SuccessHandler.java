package org.vsarthi.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.vsarthi.backend.model.Users;
import org.vsarthi.backend.service.CustomOAuth2User;
import org.vsarthi.backend.service.JwtService;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class CustomOAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public CustomOAuth2SuccessHandler(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        Users user = oAuth2User.getUser();

        String token = jwtService.generateToken(user);

        // Create a cookie with the JWT
        Cookie jwtCookie = new Cookie("jwt", token);
        jwtCookie.setHttpOnly(true);
        jwtCookie.setSecure(true); // Set to true if using HTTPS
        jwtCookie.setPath("/");
        jwtCookie.setMaxAge(3600); // 1 hour, adjust as needed

        response.addCookie(jwtCookie);

        // Create the redirect URL with the auth data
        String redirectUrl = buildRedirectUrl(user, token);

        // Perform the redirect
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    private String buildRedirectUrl(Users user, String token) throws IOException {
        // Create an object with the auth data
        AuthResponseDTO authData = new AuthResponseDTO(
                true,
                "Authentication successful",
                user,
                token
        );

        // Convert the auth data to JSON and encode it for URL
        String authDataJson = objectMapper.writeValueAsString(authData);
        String encodedAuthData = URLEncoder.encode(authDataJson, StandardCharsets.UTF_8);

        // Build the redirect URL with the encoded data
        return String.format("%s/oauth/callback?data=%s", frontendUrl, encodedAuthData);
    }

    // DTO class for auth response
    @Getter
    private static class AuthResponseDTO {
        // Getters
        private final boolean success;
        private final String message;
        private final Users user;
        private final String token;

        public AuthResponseDTO(boolean success, String message, Users user, String token) {
            this.success = success;
            this.message = message;
            this.user = user;
            this.token = token;
        }

    }
}