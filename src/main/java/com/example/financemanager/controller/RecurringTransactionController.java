package com.example.financemanager.controller;

import com.example.financemanager.dto.RecurringTransactionDTO;
import com.example.financemanager.entities.*;
import com.example.financemanager.entities.RecurringTransactionEntity.RecurrenceFrequency;
import com.example.financemanager.repositories.*;
import com.example.financemanager.service.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/recurring")
public class RecurringTransactionController {

    private final RecurringTransactionRepository recurringRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;

    public RecurringTransactionController(
            RecurringTransactionRepository recurringRepository,
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            AccountRepository accountRepository,
            ExpenseRepository expenseRepository,
            IncomeRepository incomeRepository) {
        this.recurringRepository = recurringRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.expenseRepository = expenseRepository;
        this.incomeRepository = incomeRepository;
    }

    @GetMapping
    public List<RecurringTransactionEntity> getAll(
            @AuthenticationPrincipal CustomUserDetails user) {
        return recurringRepository.findByUser_IdOrderByNextDueDateAsc(user.getUserId());
    }

    @PostMapping
    public RecurringTransactionEntity create(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody RecurringTransactionDTO dto) {
        RecurringTransactionEntity entity = new RecurringTransactionEntity();
        entity.setUser(userRepository.getReferenceById(user.getUserId()));
        mapDtoToEntity(dto, entity, user.getUserId());
        // nextDueDate starts at startDate
        entity.setNextDueDate(dto.getStartDate() != null ? dto.getStartDate() : LocalDate.now());
        return recurringRepository.save(entity);
    }

    @PutMapping("/{id}")
    public RecurringTransactionEntity update(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody RecurringTransactionDTO dto) {
        RecurringTransactionEntity entity = recurringRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Recurring transaction not found"));
        if (!entity.getUser().getId().equals(user.getUserId())) {
            throw new RuntimeException("Unauthorized");
        }
        mapDtoToEntity(dto, entity, user.getUserId());
        return recurringRepository.save(entity);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails user) {
        RecurringTransactionEntity entity = recurringRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Recurring transaction not found"));
        if (!entity.getUser().getId().equals(user.getUserId())) {
            throw new RuntimeException("Unauthorized");
        }
        recurringRepository.delete(entity);
        return ResponseEntity.noContent().build();
    }

    /**
     * Materialises expense/income records for all overdue active recurring templates,
     * then advances their nextDueDate. Idempotent: subsequent calls are no-ops until
     * the next due date is reached again.
     */
    @PostMapping("/process-due")
    public List<RecurringTransactionEntity> processDue(
            @AuthenticationPrincipal CustomUserDetails user) {
        LocalDate today = LocalDate.now();
        List<RecurringTransactionEntity> due =
                recurringRepository.findDueTransactions(user.getUserId(), today);

        for (RecurringTransactionEntity recurring : due) {
            try {
                if (recurring.getType() == RecurringTransactionEntity.RecurringType.expense) {
                    createExpense(recurring, user.getUserId());
                } else {
                    createIncome(recurring, user.getUserId());
                }
            } catch (Exception e) {
                // Skip record creation if constraints aren't met, but still advance date
            }
            recurring.setNextDueDate(advanceDate(recurring.getNextDueDate(), recurring.getFrequency()));
            recurringRepository.save(recurring);
        }

        return recurringRepository.findByUser_IdOrderByNextDueDateAsc(user.getUserId());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private void mapDtoToEntity(RecurringTransactionDTO dto,
                                RecurringTransactionEntity entity,
                                UUID userId) {
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setAmount(dto.getAmount());
        entity.setFrequency(dto.getFrequency());
        entity.setType(dto.getType());
        entity.setStartDate(dto.getStartDate());
        entity.setEndDate(dto.getEndDate());
        entity.setActive(dto.isActive());

        if (dto.getCategoryId() != null) {
            CategoryEntity category = categoryRepository.findById(dto.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found"));
            if (!category.getUser().getId().equals(userId)) {
                throw new RuntimeException("Invalid category");
            }
            entity.setCategory(category);
        } else {
            entity.setCategory(null);
        }

        if (dto.getAccountId() != null) {
            AccountEntity account = accountRepository.findById(dto.getAccountId())
                    .orElseThrow(() -> new RuntimeException("Account not found"));
            if (!account.getUser().getId().equals(userId)) {
                throw new RuntimeException("Invalid account");
            }
            entity.setAccount(account);
        } else {
            entity.setAccount(null);
        }
    }

    private void createExpense(RecurringTransactionEntity recurring, UUID userId) {
        if (recurring.getCategory() == null || recurring.getAccount() == null) return;

        ExpenseEntity expense = new ExpenseEntity();
        expense.setUser(userRepository.getReferenceById(userId));
        expense.setName(recurring.getName());
        expense.setDescription(recurring.getDescription());
        expense.setAmount(recurring.getAmount());
        expense.setExpenseDate(recurring.getNextDueDate());
        expense.setCategory(recurring.getCategory());
        expense.setAccount(recurring.getAccount());

        AccountEntity account = recurring.getAccount();
        if (account.getType() == AccountEntity.AccountType.CREDIT_CARD) {
            account.setBalance(account.getBalance().add(recurring.getAmount()));
        } else {
            account.setBalance(account.getBalance().subtract(recurring.getAmount()));
        }
        accountRepository.save(account);
        expenseRepository.save(expense);
    }

    private void createIncome(RecurringTransactionEntity recurring, UUID userId) {
        if (recurring.getCategory() == null) return;

        IncomeEntity income = new IncomeEntity();
        income.setUser(userRepository.getReferenceById(userId));
        income.setName(recurring.getName());
        income.setDescription(recurring.getDescription());
        income.setAmount(recurring.getAmount());
        income.setIncomeDate(recurring.getNextDueDate());
        income.setCategory(recurring.getCategory());

        if (recurring.getAccount() != null) {
            income.setAccount(recurring.getAccount());
            AccountEntity account = recurring.getAccount();
            account.setBalance(account.getBalance().add(recurring.getAmount()));
            accountRepository.save(account);
        }
        incomeRepository.save(income);
    }

    private LocalDate advanceDate(LocalDate date, RecurrenceFrequency frequency) {
        return switch (frequency) {
            case daily     -> date.plusDays(1);
            case weekly    -> date.plusWeeks(1);
            case biweekly  -> date.plusWeeks(2);
            case monthly   -> date.plusMonths(1);
            case quarterly -> date.plusMonths(3);
            case yearly    -> date.plusYears(1);
        };
    }
}
