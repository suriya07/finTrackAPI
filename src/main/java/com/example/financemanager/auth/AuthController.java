package com.example.financemanager.auth;

import com.example.financemanager.dto.ApiResponse;
import com.example.financemanager.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.financemanager.dto.SignupRequest;
import com.example.financemanager.dto.LoginRequest;
import com.example.financemanager.dto.AuthResponse;
import com.example.financemanager.models.User;
import com.example.financemanager.entities.UserEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@RestController
@RequestMapping("/auth")
public class AuthController {

        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;

        public AuthController(
                        UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService) {
                this.userRepository = userRepository;
                this.passwordEncoder = passwordEncoder;
                this.jwtService = jwtService;
        }

        @PostMapping("/signup")
        public ResponseEntity<ApiResponse> signup(@RequestBody SignupRequest req) {

                if (userRepository.findByEmail(req.email()).isPresent()) {
                        return ResponseEntity
                                        .status(HttpStatus.CONFLICT)
                                        .body(new ApiResponse(
                                                        true, "409",
                                                        "Email Already Exists. Please use a different email."));
                }

                UserEntity userEntity = new UserEntity();
                userEntity.setEmail(req.email());
                userEntity.setPasswordHash(
                                passwordEncoder.encode(req.password()));

                userRepository.save(userEntity);

                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .body(new ApiResponse(
                                                true, "200",
                                                "Account created successfully. Please log in."));
        }

        @PostMapping("/login")
        public ResponseEntity<ApiResponse> login(@RequestBody LoginRequest req) {

                Optional<UserEntity> userOpt = userRepository.findByEmail(req.email());

                if (userOpt.isEmpty()) {
                        return ResponseEntity
                                        .status(HttpStatus.CONFLICT)
                                        .body(new ApiResponse(
                                                        true, "409",
                                                        "Account doesn't exist. Please use a valid email."));
                }

                UserEntity user = userOpt.get();

                if (!passwordEncoder.matches(
                                req.password(), user.getPasswordHash())) {
                        throw new ResponseStatusException(
                                        HttpStatus.UNAUTHORIZED, "Invalid credentials");
                }

                String token = jwtService.generateToken(user.getId(), user.getEmail());

                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .body(new ApiResponse(
                                                true, "200",
                                                token));
        }

        @GetMapping("/me")
        public ResponseEntity<ApiResponse> getCurrentUser(
                        @AuthenticationPrincipal com.example.financemanager.service.CustomUserDetails userDetails) {
                if (userDetails == null) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(new ApiResponse(false, "401", "Unauthorized"));
                }
                return ResponseEntity.ok(new ApiResponse(true, "200", userDetails.getUsername()));
        }
}
