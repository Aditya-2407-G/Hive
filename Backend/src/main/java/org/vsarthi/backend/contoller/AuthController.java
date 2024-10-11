package org.vsarthi.backend.contoller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vsarthi.backend.model.Users;
import org.vsarthi.backend.service.UserService;

import java.util.Collections;
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
        String token = userService.verify(user);
        Users fullUserDetails = userService.getUserDetails(user.getEmail());

        // Set cookie with appropriate attributes
        Cookie jwtCookie = new Cookie("jwt", token);
        jwtCookie.setHttpOnly(true);
        jwtCookie.setSecure(true); // Enable in production
        jwtCookie.setPath("/");
        jwtCookie.setMaxAge(3600); // 1 hour
        response.addCookie(jwtCookie);

        return ResponseEntity.ok(Map.of(
                "message", "Login successful",
                "user", fullUserDetails,
                "token", token
        ));
    }

    @GetMapping("/user")
    public ResponseEntity<?> getUserDetails(@RequestParam String email, HttpServletResponse response) {

        Users user = userService.getUserDetails(email);

        return ResponseEntity.ok(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        // Invalidate the session
        request.getSession().invalidate();

        UserService.removeCookies(response);

        return ResponseEntity.ok("Logged out successfully");

    }



}