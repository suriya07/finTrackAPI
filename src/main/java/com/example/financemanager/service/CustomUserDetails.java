package com.example.financemanager.service;

import com.example.financemanager.entities.UserEntity;
import com.example.financemanager.models.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class CustomUserDetails implements UserDetails {

    private final UUID userId;
    private final String email;
    private final String password;

    public CustomUserDetails(UserEntity user) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.password = user.getPasswordHash();
    }

    public CustomUserDetails(com.example.financemanager.models.User user, UUID userId, String email, String password) {
        this.userId = userId;
        this.email = email;
        this.password = password;
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() { return password; }

    @Override
    public String getUsername() { return email; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
