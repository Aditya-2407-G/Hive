package org.vsarthi.backend.service;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.stereotype.Service;
import org.vsarthi.backend.DTO.CustomOAuth2User;
import org.vsarthi.backend.model.Users;
import org.vsarthi.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.Optional;

@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;

    @Autowired
    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        Map<String, Object> attributes = oAuth2User.getAttributes();
        String provider = userRequest.getClientRegistration().getRegistrationId();
        String email = oAuth2User.getAttribute("email");
        String googleId = oAuth2User.getAttribute("sub");
        String name = oAuth2User.getAttribute("name");

        if (email == null || googleId == null) {
            throw new IllegalStateException("Email or Google ID not found in OAuth2 user attributes");
        }

        // Check if user already exists in the database
        Optional<Users> existingUserOpt = Optional.ofNullable(userRepository.findByEmail(email));
        Users user;
        if (existingUserOpt.isEmpty()) {
            // Register a new user if not found
            user = new Users();
            user.setUsername(name);
            user.setEmail(email);
            user.setOauth2Provider(provider);
            user.setOauth2Id(googleId);
            userRepository.save(user);
        } else {
            user = existingUserOpt.get();
        }

        // Create a custom OAuth2User that includes our Users object
        return new CustomOAuth2User(oAuth2User, user);
    }
}

