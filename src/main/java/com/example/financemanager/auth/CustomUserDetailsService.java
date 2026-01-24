package com.example.financemanager.auth;

import com.example.financemanager.entities.UserEntity;
import com.example.financemanager.models.User;
import com.example.financemanager.repositories.UserRepository;
import com.example.financemanager.service.CustomUserDetails;
import org.springframework.stereotype.Service;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String input) throws UsernameNotFoundException {
        try {
            // Try to parse as UUID first since JWT subject is UserId
            java.util.UUID id = java.util.UUID.fromString(input);
            UserEntity user = userRepository.findById(id)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + input));
            return new CustomUserDetails(user);
        } catch (IllegalArgumentException e) {
            // Fallback to email lookup if not a UUID
            UserEntity user = userRepository.findByEmail(input)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + input));
            return new CustomUserDetails(user);
        }
    }
}
