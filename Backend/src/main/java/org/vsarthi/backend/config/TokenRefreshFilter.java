package org.vsarthi.backend.config;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.vsarthi.backend.service.JwtService;
import org.vsarthi.backend.service.UserDetailsServiceImpl;
import org.vsarthi.backend.service.UserService;

import java.io.IOException;

@Component
public class TokenRefreshFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;
    private final UserService userService;

    @Autowired
    public TokenRefreshFilter(JwtService jwtService, UserDetailsServiceImpl userDetailsService, @Lazy UserService userService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        System.out.println("TokenRefreshFilter: Processing request to " + request.getRequestURI() + " with method " + request.getMethod());

        String accessToken = extractTokenFromCookie(request, "accessToken");
        String refreshToken = extractTokenFromCookie(request, "refreshToken");

        System.out.println("Access Token: " + (accessToken != null ? "Present" : "Not Present"));
        System.out.println("Refresh Token: " + (refreshToken != null ? "Present" : "Not Present"));

        try {
            if (accessToken != null) {
                System.out.println("Validating Access Token...");
                if (jwtService.isTokenValid(accessToken)) {
                    System.out.println("Access Token is valid. Setting authentication.");
                    setAuthentication(accessToken, request);
                    request.setAttribute("validatedAccessToken", accessToken);
                } else if (refreshToken != null) {
                    System.out.println("Access Token is invalid or expired. Refreshing with Refresh Token.");
                    handleTokenRefresh(refreshToken, request, response);
                } else {
                    System.out.println("No valid tokens available. Skipping authentication.");
                }
            } else if (refreshToken != null) {
                System.out.println("Access Token missing but Refresh Token present. Attempting to refresh.");
                handleTokenRefresh(refreshToken, request, response);
            }
        } catch (ExpiredJwtException e) {
            System.out.println("Access Token expired. Trying to refresh using Refresh Token.");
            if (refreshToken != null) {
                handleTokenRefresh(refreshToken, request, response);
            } else {
                System.out.println("Refresh Token not found. Session expired. Prompting user to log in again.");
                handleAuthenticationFailure(response, "Session expired. Please log in again.");
                return;
            }
        } catch (Exception e) {
            System.out.println("An error occurred during authentication: " + e.getMessage());
            handleAuthenticationFailure(response, "Authentication failed. Please log in again.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void handleTokenRefresh(String refreshToken, HttpServletRequest request, HttpServletResponse response) throws IOException {
        System.out.println("Handling token refresh...");
        try {

            String newAccessToken = userService.refreshedAccessToken(refreshToken);

            System.out.println("Access Token refreshed successfully. Setting new cookies.");
            setNewTokenCookies(response, newAccessToken);
            request.setAttribute("newAccessToken", newAccessToken);
            setAuthentication(newAccessToken, request);
        } catch (Exception e) {
            System.out.println("Failed to refresh tokens: " + e.getMessage());
            handleAuthenticationFailure(response, "Failed to refresh token. Please log in again.");
        }
    }

    private void setAuthentication(String token, HttpServletRequest request) {
        System.out.println("Setting authentication for token.");
        String email = jwtService.extractEmail(token);
        UserDetails userDetails = userDetailsService.loadUserByEmail(email);
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        if (request != null) {
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        }
        SecurityContextHolder.getContext().setAuthentication(authToken);
        System.out.println("Authentication set for user: " + email);
    }

    private void setNewTokenCookies(HttpServletResponse response, String newAccessToken) {
        ResponseCookie accessTokenCookie = ResponseCookie.from("accessToken", newAccessToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(3600) // 1 hour
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, accessTokenCookie.toString());
        System.out.println("New token cookies set.");
    }

    private void handleAuthenticationFailure(HttpServletResponse response, String message) throws IOException {
        System.out.println("Authentication failed. Clearing cookies and returning error message.");
        clearAuthCookies(response);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write(message);
    }

    private void clearAuthCookies(HttpServletResponse response) {
        System.out.println("Clearing authentication cookies.");
        ResponseCookie clearAccessTokenCookie = ResponseCookie.from("accessToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .build();
        ResponseCookie clearRefreshTokenCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/api/auth/refresh")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, clearAccessTokenCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, clearRefreshTokenCookie.toString());
        System.out.println("Cookies cleared.");
    }

    private String extractTokenFromCookie(HttpServletRequest request, String cookieName) {
        System.out.println("Extracting " + cookieName + " from cookies.");
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(cookieName)) {
                    System.out.println(cookieName + " found.");
                    return cookie.getValue();
                }
            }
        }
        System.out.println(cookieName + " not found.");
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        boolean shouldNotFilter = uri.contains("/api/auth/register") ||
                uri.contains("/api/auth/login") ||
                uri.contains("/api/auth/refresh") ||
                uri.contains("/oauth2") ||
                uri.contains("/api/auth/logout") ||
                uri.contains("/ws");
        System.out.println("Should not filter: " + shouldNotFilter);
        return shouldNotFilter;
    }
}
