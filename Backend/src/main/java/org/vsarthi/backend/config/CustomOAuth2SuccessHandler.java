package org.vsarthi.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.vsarthi.backend.model.Users;
import org.vsarthi.backend.DTO.CustomOAuth2User;
import org.vsarthi.backend.service.UserService;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component

public class CustomOAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public CustomOAuth2SuccessHandler(@Lazy UserService userService, ObjectMapper objectMapper) {
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        Users user = oAuth2User.getUser();

        UserService.TokenPair tokens = userService.generateTokens(user);

        // Create a cookie with the access token
        ResponseCookie jwtCookie = ResponseCookie.from("accessToken", tokens.accessToken)
                .httpOnly(true)
                .secure(true) // Set to true if using HTTPS
                .path("/")
                .maxAge(3600) // 1 hour
                .build();

        // Create a cookie with the refresh token
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", tokens.refreshToken)
                .httpOnly(true)
                .secure(true) // Set to true if using HTTPS
                .path("/") // not the best practice, but for simplicity
                .maxAge(604800) // 1 week
                .build();

        response.addHeader("Set-Cookie", jwtCookie.toString());
        response.addHeader("Set-Cookie", refreshCookie.toString());

        // Create the redirect URL with the auth data
        String redirectUrl = buildRedirectUrl(user, tokens);

        // Perform the redirect
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    private String buildRedirectUrl(Users user, UserService.TokenPair tokens) throws IOException {
        // Create a map with the auth data
        Map<String, Object> authData = Map.of(
                "message", "Authentication successful",
                "user", user,
                "tokens", tokens
        );

        // Convert the auth data to JSON and encode it for URL
        String authDataJson = objectMapper.writeValueAsString(authData);
        String encodedAuthData = URLEncoder.encode(authDataJson, StandardCharsets.UTF_8);

        // Build the redirect URL with the encoded data
        return String.format("%s/oauth/callback?data=%s", frontendUrl, encodedAuthData);
    }

}