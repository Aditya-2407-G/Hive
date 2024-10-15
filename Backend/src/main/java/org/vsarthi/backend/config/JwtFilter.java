package org.vsarthi.backend.config;

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
        String authHeader = request.getHeader("Authorization");
        String email = null;

        if(authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        if(token == null) {
            Cookie[] cookies = request.getCookies();

            if (cookies != null) {
                Cookie jwtCookie = Arrays.stream(cookies)
                        .filter(cookie -> "accessToken".equals(cookie.getName()))
                        .findFirst()
                        .orElse(null);

                if (jwtCookie != null) {
                    token = jwtCookie.getValue();
                    email = jwtService.extractEmail(token);
                }
            }
        } else {
            email = jwtService.extractEmail(token);
        }

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = context.getBean(UserDetailsServiceImpl.class).loadUserByEmail(email);

            if (jwtService.validateToken(token, (UserPrincipal) userDetails)) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
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