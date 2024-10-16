package org.vsarthi.backend.config;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.vsarthi.backend.model.UserPrincipal;
import org.vsarthi.backend.service.JwtService;
import org.vsarthi.backend.service.UserDetailsServiceImpl;

import java.io.IOException;
import java.util.Arrays;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ApplicationContext context;

    @Autowired
    public JwtFilter(JwtService jwtService, ApplicationContext context) {
        this.jwtService = jwtService;
        this.context = context;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        System.out.println("JwtFilter: Processing request to " + request.getRequestURI() + " with method " + request.getMethod());

        String token = null;
        String email = null;

        // Check if TokenRefreshFilter has already validated the token
        Object validatedToken = request.getAttribute("validatedAccessToken");
        if (validatedToken != null) {
            token = (String) validatedToken;
            System.out.println("JwtFilter: Using validated access token from TokenRefreshFilter");
        } else {
            // If no validated token, check for a new token set by TokenRefreshFilter
            String newToken = (String) request.getAttribute("newAccessToken");
            if (newToken != null) {
                token = newToken;
                System.out.println("JwtFilter: Using new access token set by TokenRefreshFilter");
            } else {
                // If no new token, check the request as before
                token = extractTokenFromRequest(request);
            }
        }

        if (token != null) {
            try {
                email = jwtService.extractEmail(token);
                if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = context.getBean(UserDetailsServiceImpl.class).loadUserByEmail(email);
                    if (jwtService.validateToken(token, (UserPrincipal) userDetails)) {
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        System.out.println("JwtFilter: Authentication set for user: " + email);
                    } else {
                        System.out.println("JwtFilter: Token validation failed for user: " + email);
                    }
                }
            } catch (ExpiredJwtException e) {
                System.out.println("JwtFilter: Token has expired. This should have been handled by TokenRefreshFilter.");
            } catch (Exception e) {
                System.out.println("JwtFilter: Error processing token: " + e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("accessToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().contains("/api/auth/register") ||
                request.getRequestURI().contains("/api/auth/login") ||
                request.getRequestURI().contains("/api/auth/refresh") ||
                request.getRequestURI().contains("/oauth2") ||
                request.getRequestURI().contains("/api/auth/logout") ||
                request.getRequestURI().contains("/ws");
    }
}