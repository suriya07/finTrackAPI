package com.example.financemanager.controller;

import com.example.financemanager.dto.RecurringTransactionDTO;
import com.example.financemanager.entities.RecurringTransactionEntity;
import com.example.financemanager.service.CustomUserDetails;
import com.example.financemanager.service.RecurringTransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/recurring")
public class RecurringTransactionController {

    private final RecurringTransactionService recurringService;

    public RecurringTransactionController(RecurringTransactionService recurringService) {
        this.recurringService = recurringService;
    }

    @GetMapping
    public List<RecurringTransactionEntity> getAll(@AuthenticationPrincipal CustomUserDetails user) {
        return recurringService.getAll(user.getUserId());
    }

    @PostMapping
    public RecurringTransactionEntity create(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody RecurringTransactionDTO dto) {
        return recurringService.create(user.getUserId(), dto);
    }

    @PutMapping("/{id}")
    public RecurringTransactionEntity update(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody RecurringTransactionDTO dto) {
        return recurringService.update(user.getUserId(), id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails user) {
        recurringService.delete(user.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Materialises expense/income records for all overdue active recurring templates,
     * then advances their nextDueDate. Idempotent: subsequent calls are no-ops until
     * the next due date is reached again. Also runs automatically via the scheduler.
     */
    @PostMapping("/process-due")
    public List<RecurringTransactionEntity> processDue(@AuthenticationPrincipal CustomUserDetails user) {
        return recurringService.processDueForUser(user.getUserId());
    }
}
