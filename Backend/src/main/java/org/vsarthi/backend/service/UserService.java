package org.vsarthi.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.vsarthi.backend.model.Users;
import org.vsarthi.backend.repository.UserRepository;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

@Service

public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public static void removeCookies(HttpServletResponse response) {
        clearCookie("accessToken", response);
        clearCookie("JSESSIONID", response);
    }

    private static void clearCookie(String cookieName, HttpServletResponse response) {
        Cookie cookie = new Cookie(cookieName, null);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    public String verify(Users user) {
        Users existingUser = userRepository.findByEmail(user.getEmail());
        if (existingUser == null) {
            throw new UsernameNotFoundException("User not found");
        }

        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(user.getEmail(), user.getPassword()));

        if (authentication.isAuthenticated()) {
            return jwtService.generateAccessToken(existingUser);
        } else {
            throw new IllegalArgumentException("User not authenticated");
        }
    }

    public Users getUserDetails(String email) {
        return userRepository.findByEmail(email);
    }

    public Users register(Users user) {
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    // New method to generate both access and refresh tokens
    public TokenPair generateTokens(Users user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        // Store refresh token in the database
        user.setRefreshToken(refreshToken);
        userRepository.save(user);

        return new TokenPair(accessToken, refreshToken);
    }

    // New method to authenticate and generate tokens
    public TokenPair authenticateAndGenerateTokens(String email, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
        );

        if (authentication.isAuthenticated()) {
            Users user = userRepository.findByEmail(email);
            if (user == null) {
                throw new UsernameNotFoundException("User not found");
            }
            return generateTokens(user);
        } else {
            throw new IllegalArgumentException("Invalid email or password");
        }
    }


    // generate a refreshed access token

    public String refreshedAccessToken(String refreshToken) {
        String email = jwtService.extractEmail(refreshToken);
        Users user = userRepository.findByEmail(email);

        if (user == null || !refreshToken.equals(user.getRefreshToken())) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        return jwtService.generateAccessToken(user);
    }



    public TokenPair refreshTokens(String refreshToken) {
        String email = jwtService.extractEmail(refreshToken);
        Users user = userRepository.findByEmail(email);

        if (user == null || !refreshToken.equals(user.getRefreshToken())) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        return generateTokens(user);
    }

    public Users findOrCreateUser(String googleId, String email, String name) {
        Users user = userRepository.findByOauth2Id(googleId);
        if (user == null) {
            user = new Users();
            user.setOauth2Id(googleId);
            user.setEmail(email);
            user.setUsername(name);
            userRepository.save(user);
        }
        return user;
    }

    // Inner class to represent a pair of tokens
    public static class TokenPair {
        public final String accessToken;
        public final String refreshToken;

        public TokenPair(String accessToken, String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
    }
}