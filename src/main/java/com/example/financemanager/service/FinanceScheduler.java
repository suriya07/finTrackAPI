package com.example.financemanager.service;

import com.example.financemanager.entities.DebtEntity;
import com.example.financemanager.entities.RecurringTransactionEntity;
import com.example.financemanager.entities.UserEntity;
import com.example.financemanager.repositories.DebtRepository;
import com.example.financemanager.repositories.RecurringTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Background jobs that make recurring transactions and reminders actually fire
 * without a client poking the API. Scheduling is enabled via
 * {@code @EnableScheduling} on the application class.
 */
@Component
public class FinanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(FinanceScheduler.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final RecurringTransactionService recurringService;
    private final RecurringTransactionRepository recurringRepository;
    private final DebtRepository debtRepository;
    private final EmailService emailService;

    @Value("${app.reminders.enabled:true}")
    private boolean remindersEnabled;

    @Value("${app.reminders.look-ahead-days:3}")
    private long lookAheadDays;

    public FinanceScheduler(RecurringTransactionService recurringService,
            RecurringTransactionRepository recurringRepository,
            DebtRepository debtRepository,
            EmailService emailService) {
        this.recurringService = recurringService;
        this.recurringRepository = recurringRepository;
        this.debtRepository = debtRepository;
        this.emailService = emailService;
    }

    /** Materialise all due recurring transactions, daily at 01:00. */
    @Scheduled(cron = "${app.scheduling.recurring-cron:0 0 1 * * *}")
    public void processRecurringTransactions() {
        try {
            int count = recurringService.processAllDue();
            if (count > 0) {
                log.info("Recurring scheduler processed {} due transaction(s)", count);
            }
        } catch (Exception e) {
            log.error("Recurring scheduler run failed", e);
        }
    }

    /** Email each user a digest of debts and recurring transactions due soon, daily at 08:00. */
    @Scheduled(cron = "${app.scheduling.reminder-cron:0 0 8 * * *}")
    @Transactional(readOnly = true)
    public void sendDueReminders() {
        if (!remindersEnabled) {
            return;
        }
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(lookAheadDays);

        // Preserve insertion order per user so the digest reads naturally.
        Map<UserEntity, List<EmailService.ReminderItem>> itemsByUser = new LinkedHashMap<>();

        for (DebtEntity debt : debtRepository.findByDueDateBetween(today, horizon)) {
            itemsByUser.computeIfAbsent(debt.getUser(), u -> new ArrayList<>())
                    .add(new EmailService.ReminderItem(
                            debt.getName(), "Debt repayment", formatAmount(debt.getAmount()),
                            debt.getDueDate().format(DATE_FMT), false));
        }

        for (RecurringTransactionEntity r : recurringRepository.findUpcoming(today, horizon)) {
            boolean isIncome =
                    r.getType() == RecurringTransactionEntity.RecurringType.income;
            // Surface the category (what the materialised expense/income is filed under),
            // falling back to the type label when no category is set.
            String detail = r.getCategory() != null
                    ? r.getCategory().getName()
                    : capitalize(r.getType().name());
            itemsByUser.computeIfAbsent(r.getUser(), u -> new ArrayList<>())
                    .add(new EmailService.ReminderItem(
                            r.getName(), detail, formatAmount(r.getAmount()),
                            r.getNextDueDate().format(DATE_FMT), isIncome));
        }

        for (Map.Entry<UserEntity, List<EmailService.ReminderItem>> entry : itemsByUser.entrySet()) {
            emailService.sendReminderDigest(
                    entry.getKey().getEmail(), lookAheadDays, entry.getValue());
        }

        if (!itemsByUser.isEmpty()) {
            log.info("Reminder scheduler notified {} user(s)", itemsByUser.size());
        }
    }

    private String formatAmount(java.math.BigDecimal amount) {
        return amount == null ? "" : String.format("₹%,.2f", amount);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
