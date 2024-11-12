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

        String platform = request.getParameter("platform");

        if ("mobile".equalsIgnoreCase(platform)) {
            handleMobileResponse(response, user, tokens);
        } else {
            handleWebResponse(request, response, user, tokens);
        }
    }

    private void handleMobileResponse(HttpServletResponse response, Users user, UserService.TokenPair tokens) throws IOException {
        // Set response headers for mobile
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");

        // Create response data
        Map<String, Object> authData = Map.of(
                "message", "Authentication successful",
                "user", user,
                "tokens", tokens
        );

        // Write JSON response
        response.getWriter().write(objectMapper.writeValueAsString(authData));
    }

    private void handleWebResponse(HttpServletRequest request, HttpServletResponse response, Users user, UserService.TokenPair tokens) throws IOException {
        // Set cookies for web
        setAuthCookies(response, tokens);

        // Redirect to frontend
        String redirectUrl = buildRedirectUrl(user, tokens);
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    private void setAuthCookies(HttpServletResponse response, UserService.TokenPair tokens) {
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

        response.addHeader("Set-Cookie", jwtCookie.toString());
        response.addHeader("Set-Cookie", refreshCookie.toString());
    }

    private String buildRedirectUrl(Users user, UserService.TokenPair tokens) throws IOException {
        Map<String, Object> authData = Map.of(
                "message", "Authentication successful",
                "user", user,
                "tokens", tokens
        );

        String authDataJson = objectMapper.writeValueAsString(authData);
        String encodedAuthData = URLEncoder.encode(authDataJson, StandardCharsets.UTF_8);

        return String.format("%s/oauth/callback?data=%s", frontendUrl, encodedAuthData);
    }
}