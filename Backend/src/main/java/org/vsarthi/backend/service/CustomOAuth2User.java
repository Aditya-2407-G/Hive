package org.vsarthi.backend.service;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.vsarthi.backend.model.Users;

import java.util.Collection;
import java.util.Map;

// Custom OAuth2User class to include our Users object
public class CustomOAuth2User implements OAuth2User {
    private final OAuth2User oauth2User;
    @Getter
    private final Users user;

    public CustomOAuth2User(OAuth2User oauth2User, Users user) {
        this.oauth2User = oauth2User;
        this.user = user;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return oauth2User.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return oauth2User.getAuthorities();
    }

    @Override
    public String getName() {
        return oauth2User.getName();
    }

}
