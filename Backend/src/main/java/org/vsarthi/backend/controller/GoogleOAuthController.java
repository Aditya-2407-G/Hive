package org.vsarthi.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.client.RestTemplate;
import org.vsarthi.backend.model.Users;
import org.vsarthi.backend.service.UserService;

import java.util.HashMap;
import java.util.Map;

@Controller
public class GoogleOAuthController {

    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${google.client.id}")
    private String clientId;

    @Value("${google.client.secret}")
    private String clientSecret;

    @Value("${google.redirect.uri}")
    private String redirectUri;

    public GoogleOAuthController(UserService userService, ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.userService = userService;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    @PostMapping("/api/auth/oauth/google")
    public ResponseEntity<?> googleLogin(@RequestBody Map<String, String> requestBody) throws Exception {
        String authorizationCode = requestBody.get("code");

        // Step 1: Exchange authorization code for tokens
        String tokenEndpoint = "https://oauth2.googleapis.com/token";
        Map<String, String> tokenRequest = new HashMap<>();
        tokenRequest.put("code", authorizationCode);
        tokenRequest.put("client_id", clientId);
        tokenRequest.put("client_secret", clientSecret);
        tokenRequest.put("redirect_uri", redirectUri);
        tokenRequest.put("grant_type", "authorization_code");

        ResponseEntity<String> tokenResponse = restTemplate.postForEntity(tokenEndpoint, tokenRequest, String.class);
        JsonNode tokenJson = objectMapper.readTree(tokenResponse.getBody());
        String accessToken = tokenJson.get("access_token").asText();

        // Step 2: Retrieve user info from Google
        String userInfoEndpoint = "https://www.googleapis.com/oauth2/v3/userinfo";
        ResponseEntity<String> userInfoResponse = restTemplate.getForEntity(userInfoEndpoint + "?access_token=" + accessToken, String.class);
        JsonNode userInfo = objectMapper.readTree(userInfoResponse.getBody());

        // Extract user information from Google response
        String googleId = userInfo.get("sub").asText();
        String email = userInfo.get("email").asText();
        String name = userInfo.get("name").asText();

        // Step 3: Find or create the user in your system
        Users user = userService.findOrCreateUser(googleId, email, name);

        // Step 4: Generate your app's tokens
        UserService.TokenPair tokens = userService.generateTokens(user);

        // Step 5: Return the tokens in JSON response
        Map<String, Object> authData = Map.of(
                "message", "Authentication successful",
                "user", user,
                "tokens", tokens
        );
        return ResponseEntity.ok(authData);
    }
}
