package com.example.financemanager.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.from:noreply@fintrack.local}")
    private String fromAddress;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        String resetLink = frontendUrl + "/reset-password?token=" + resetToken;

        if (mailSender == null) {
            log.warn("=== PASSWORD RESET (mail not configured) ===");
            log.warn("To: {}", toEmail);
            log.warn("Reset link: {}", resetLink);
            log.warn("============================================");
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("FinTrack - Reset Your Password");
        message.setText(
                "You requested a password reset for your FinTrack account.\n\n" +
                "Click the link below to reset your password (valid for 15 minutes):\n\n" +
                resetLink + "\n\n" +
                "If you did not request this, you can safely ignore this email.\n\n" +
                "— FinTrack");

        try {
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send reset email to {}: {}", toEmail, e.getMessage());
            log.warn("Reset link (fallback): {}", resetLink);
        }
    }
}
