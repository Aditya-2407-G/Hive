package org.vsarthi.backend.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.vsarthi.backend.model.Users;
import org.vsarthi.backend.repository.UserRepository;

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

        clearCookie("jwt", response);
        clearCookie("JSESSIONID", response);
    }

    private static void clearCookie(String cookieName, HttpServletResponse response) {
        Cookie cookie = new Cookie(cookieName, null);
        cookie.setPath("/");  // Make sure it matches the cookie's original path
        cookie.setHttpOnly(true);  // Optional: Keep the cookie HttpOnly if it was initially set as HttpOnly
        cookie.setMaxAge(0);  // Expire the cookie
        response.addCookie(cookie);  // Add it to the response to clear it on the client-side
    }

    public String verify(Users user) {
        Users existingUser = userRepository.findByEmail(user.getEmail());
        if (existingUser == null) {
            throw new UsernameNotFoundException("User not found");
        }

        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(user.getEmail(), user.getPassword()));

        if (authentication.isAuthenticated()) {
            return jwtService.generateToken(existingUser);
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

}