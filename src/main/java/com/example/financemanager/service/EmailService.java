package com.example.financemanager.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Sends branded HTML emails (password reset + reminder digests). Falls back to
 * logging when mail is not configured (e.g. local dev) so the content is still
 * observable. All messages are wrapped in a single Frugality-branded shell so
 * the look stays consistent.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private static final String BRAND = "Frugality";
    private static final String BRAND_INDIGO = "#6366F1";

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.from:noreply@frugality.app}")
    private String fromAddress;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    /** One reminder line in the upcoming-payments digest. */
    public record ReminderItem(String name, String detail, String amount,
            String dueDate, boolean isIncome) {}

    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        String resetLink = frontendUrl + "/reset-password?token=" + resetToken;

        String content = """
                <h1 style="margin:0 0 12px;font-size:20px;color:#111827;">Reset your password</h1>
                <p style="margin:0 0 20px;color:#4b5563;">
                  We received a request to reset your %s password. This link is valid for
                  <strong>15 minutes</strong>.
                </p>
                <p style="margin:0 0 24px;text-align:center;">
                  <a href="%s" style="display:inline-block;background:%s;color:#ffffff;
                     text-decoration:none;font-weight:600;padding:12px 28px;border-radius:10px;">
                    Reset password
                  </a>
                </p>
                <p style="margin:0 0 8px;color:#9ca3af;font-size:13px;">
                  Or paste this link into your browser:
                </p>
                <p style="margin:0 0 20px;word-break:break-all;font-size:13px;">
                  <a href="%s" style="color:%s;">%s</a>
                </p>
                <p style="margin:0;color:#9ca3af;font-size:13px;">
                  If you didn't request this, you can safely ignore this email.
                </p>
                """.formatted(BRAND, resetLink, BRAND_INDIGO, resetLink, BRAND_INDIGO, resetLink);

        send(toEmail, BRAND + " · Reset your password", wrap(content),
                "Reset link: " + resetLink);
    }

    /** Branded digest of debts + recurring transactions due soon. */
    public void sendReminderDigest(String toEmail, long days, List<ReminderItem> items) {
        StringBuilder cards = new StringBuilder();
        for (ReminderItem item : items) {
            cards.append(renderCard(item));
        }

        String content = """
                <h1 style="margin:0 0 6px;font-size:20px;color:#111827;">Upcoming payments</h1>
                <p style="margin:0 0 20px;color:#6b7280;">
                  Here's what's due in the next %d day%s.
                </p>
                %s
                """.formatted(days, days == 1 ? "" : "s", cards.toString());

        send(toEmail, BRAND + " · Upcoming payments", wrap(content), plainFallback(items));
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    private String renderCard(ReminderItem item) {
        String amountColor = item.isIncome() ? "#059669" : "#dc2626";
        String kindLabel = item.isIncome() ? "Income" : "Due";
        return """
                <div style="border:1px solid #eef2ff;border-radius:12px;padding:12px 14px;margin-bottom:10px;">
                  <table width="100%%" cellpadding="0" cellspacing="0"><tr>
                    <td style="vertical-align:top;">
                      <div style="font-weight:600;color:#111827;font-size:15px;">%s</div>
                      <div style="color:#6b7280;font-size:13px;margin-top:2px;">%s · due %s</div>
                    </td>
                    <td style="text-align:right;vertical-align:top;white-space:nowrap;padding-left:12px;">
                      <div style="font-weight:700;color:%s;font-size:15px;">%s</div>
                      <div style="font-size:11px;color:#9ca3af;margin-top:2px;">%s</div>
                    </td>
                  </tr></table>
                </div>
                """.formatted(escape(item.name()), escape(item.detail()),
                escape(item.dueDate()), amountColor, escape(item.amount()), kindLabel);
    }

    /** Wraps body [content] in the Frugality-branded shell (header + footer). */
    private String wrap(String content) {
        return """
                <div style="background:#f3f4f6;padding:24px 12px;font-family:-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
                  <div style="max-width:520px;margin:0 auto;background:#ffffff;border-radius:16px;overflow:hidden;border:1px solid #eef2ff;">
                    <div style="background:%s;padding:20px 24px;">
                      <span style="display:inline-block;width:34px;height:34px;line-height:34px;text-align:center;background:#ffffff;color:%s;border-radius:9px;font-size:19px;font-weight:700;vertical-align:middle;">&#8377;</span>
                      <span style="color:#ffffff;font-size:19px;font-weight:700;margin-left:10px;vertical-align:middle;">%s</span>
                    </div>
                    <div style="padding:24px;color:#111827;font-size:15px;line-height:1.6;">
                      %s
                    </div>
                    <div style="padding:16px 24px;background:#f9fafb;color:#9ca3af;font-size:12px;text-align:center;">
                      You're receiving this because you have a %s account.
                    </div>
                  </div>
                </div>
                """.formatted(BRAND_INDIGO, BRAND_INDIGO, BRAND, content, BRAND);
    }

    private String plainFallback(List<ReminderItem> items) {
        StringBuilder sb = new StringBuilder("Upcoming payments:\n");
        for (ReminderItem i : items) {
            sb.append("• ").append(i.name()).append(" (").append(i.detail())
                    .append(") ").append(i.amount()).append(" due ").append(i.dueDate())
                    .append('\n');
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Transport
    // -------------------------------------------------------------------------

    /** Sends an HTML email, logging [textFallback] when mail is unconfigured. */
    private void send(String toEmail, String subject, String html, String textFallback) {
        if (mailSender == null) {
            log.info("=== EMAIL (mail not configured) ===");
            log.info("To: {} | Subject: {}", toEmail, subject);
            log.info("{}", textFallback);
            log.info("===================================");
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress, BRAND);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true); // html = true
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
            log.warn("Fallback: {}", textFallback);
        }
    }

    /** Minimal HTML escaping for user-supplied strings injected into templates. */
    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
