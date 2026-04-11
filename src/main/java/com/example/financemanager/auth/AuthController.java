package com.example.financemanager.auth;

import com.example.financemanager.dto.ApiResponse;
import com.example.financemanager.dto.ForgotPasswordRequest;
import com.example.financemanager.dto.ResetPasswordRequest;
import com.example.financemanager.entities.PasswordResetTokenEntity;
import com.example.financemanager.repositories.PasswordResetTokenRepository;
import com.example.financemanager.repositories.UserRepository;
import com.example.financemanager.service.EmailService;
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
import com.example.financemanager.dto.ChangePasswordRequest;
import com.example.financemanager.entities.UserEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;
        private final PasswordResetTokenRepository passwordResetTokenRepository;
        private final EmailService emailService;

        public AuthController(
                        UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        PasswordResetTokenRepository passwordResetTokenRepository,
                        EmailService emailService) {
                this.userRepository = userRepository;
                this.passwordEncoder = passwordEncoder;
                this.jwtService = jwtService;
                this.passwordResetTokenRepository = passwordResetTokenRepository;
                this.emailService = emailService;
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

        @PutMapping("/change-password")
        public ResponseEntity<ApiResponse> changePassword(
                        @AuthenticationPrincipal com.example.financemanager.service.CustomUserDetails userDetails,
                        @RequestBody ChangePasswordRequest req) {

                if (userDetails == null) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(new ApiResponse(false, "401", "Unauthorized"));
                }

                UUID userId = userDetails.getUserId();
                UserEntity user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "User not found"));

                if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                        .body(new ApiResponse(false, "400", "Current password is incorrect"));
                }

                user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
                userRepository.save(user);

                return ResponseEntity.ok(new ApiResponse(true, "200", "Password changed successfully"));
        }

        @PostMapping("/forgot-password")
        public ResponseEntity<ApiResponse> forgotPassword(@RequestBody ForgotPasswordRequest req) {
                // Always return the same response to prevent email enumeration
                String genericMessage = "If an account with that email exists, a password reset link has been sent.";

                Optional<UserEntity> userOpt = userRepository.findByEmail(req.email());
                if (userOpt.isEmpty()) {
                        return ResponseEntity.ok(new ApiResponse(true, "200", genericMessage));
                }

                UserEntity user = userOpt.get();

                // Invalidate any existing tokens for this user before creating a new one
                passwordResetTokenRepository.deleteByUser(user);

                String rawToken = UUID.randomUUID().toString().replace("-", "");
                Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);

                passwordResetTokenRepository.save(
                        new PasswordResetTokenEntity(rawToken, user, expiresAt));

                emailService.sendPasswordResetEmail(user.getEmail(), rawToken);

                return ResponseEntity.ok(new ApiResponse(true, "200", genericMessage));
        }

        @PostMapping("/reset-password")
        public ResponseEntity<ApiResponse> resetPassword(@RequestBody ResetPasswordRequest req) {
                Optional<PasswordResetTokenEntity> tokenOpt =
                        passwordResetTokenRepository.findByToken(req.token());

                if (tokenOpt.isEmpty()) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(new ApiResponse(false, "400", "Invalid or expired reset token"));
                }

                PasswordResetTokenEntity tokenEntity = tokenOpt.get();

                if (tokenEntity.isUsed() || Instant.now().isAfter(tokenEntity.getExpiresAt())) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(new ApiResponse(false, "400", "Invalid or expired reset token"));
                }

                UserEntity user = tokenEntity.getUser();
                user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
                userRepository.save(user);

                tokenEntity.setUsed(true);
                passwordResetTokenRepository.save(tokenEntity);

                return ResponseEntity.ok(new ApiResponse(true, "200", "Password reset successfully. Please log in."));
        }
}
