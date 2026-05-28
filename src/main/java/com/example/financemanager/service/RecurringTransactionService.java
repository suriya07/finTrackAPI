package com.example.financemanager.service;

import com.example.financemanager.dto.RecurringTransactionDTO;
import com.example.financemanager.entities.AccountEntity;
import com.example.financemanager.entities.CategoryEntity;
import com.example.financemanager.entities.ExpenseEntity;
import com.example.financemanager.entities.IncomeEntity;
import com.example.financemanager.entities.RecurringTransactionEntity;
import com.example.financemanager.entities.RecurringTransactionEntity.RecurrenceFrequency;
import com.example.financemanager.repositories.AccountRepository;
import com.example.financemanager.repositories.CategoryRepository;
import com.example.financemanager.repositories.ExpenseRepository;
import com.example.financemanager.repositories.IncomeRepository;
import com.example.financemanager.repositories.RecurringTransactionRepository;
import com.example.financemanager.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Owns recurring-transaction CRUD plus the materialisation logic that turns a
 * due template into a concrete expense/income and advances its next due date.
 * Shared by {@code RecurringTransactionController} (manual trigger) and the
 * scheduled job so the behaviour can never diverge.
 */
@Service
public class RecurringTransactionService {

    private static final Logger log = LoggerFactory.getLogger(RecurringTransactionService.class);

    private final RecurringTransactionRepository recurringRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final AccountBalanceService balanceService;

    public RecurringTransactionService(RecurringTransactionRepository recurringRepository,
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            AccountRepository accountRepository,
            ExpenseRepository expenseRepository,
            IncomeRepository incomeRepository,
            AccountBalanceService balanceService) {
        this.recurringRepository = recurringRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.expenseRepository = expenseRepository;
        this.incomeRepository = incomeRepository;
        this.balanceService = balanceService;
    }

    @Transactional(readOnly = true)
    public List<RecurringTransactionEntity> getAll(UUID userId) {
        return recurringRepository.findByUser_IdOrderByNextDueDateAsc(userId);
    }

    @Transactional
    public RecurringTransactionEntity create(UUID userId, RecurringTransactionDTO dto) {
        RecurringTransactionEntity entity = new RecurringTransactionEntity();
        entity.setUser(userRepository.getReferenceById(userId));
        mapDtoToEntity(dto, entity, userId);
        entity.setNextDueDate(dto.getStartDate() != null ? dto.getStartDate() : LocalDate.now());
        return recurringRepository.save(entity);
    }

    @Transactional
    public RecurringTransactionEntity update(UUID userId, UUID id, RecurringTransactionDTO dto) {
        RecurringTransactionEntity entity = recurringRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurring transaction not found"));
        if (!entity.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized");
        }
        mapDtoToEntity(dto, entity, userId);
        return recurringRepository.save(entity);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        RecurringTransactionEntity entity = recurringRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurring transaction not found"));
        if (!entity.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized");
        }
        recurringRepository.delete(entity);
    }

    /** Manual trigger: process this user's due templates and return their updated list. */
    @Transactional
    public List<RecurringTransactionEntity> processDueForUser(UUID userId) {
        LocalDate today = LocalDate.now();
        for (RecurringTransactionEntity recurring : recurringRepository.findDueTransactions(userId, today)) {
            materialiseAndAdvance(recurring);
        }
        return recurringRepository.findByUser_IdOrderByNextDueDateAsc(userId);
    }

    /** Scheduled trigger: process every user's due templates. Returns count processed. */
    @Transactional
    public int processAllDue() {
        LocalDate today = LocalDate.now();
        List<RecurringTransactionEntity> due = recurringRepository.findAllDueTransactions(today);
        for (RecurringTransactionEntity recurring : due) {
            materialiseAndAdvance(recurring);
        }
        return due.size();
    }

    private void materialiseAndAdvance(RecurringTransactionEntity recurring) {
        try {
            if (recurring.getType() == RecurringTransactionEntity.RecurringType.expense) {
                createExpense(recurring);
            } else {
                createIncome(recurring);
            }
        } catch (Exception e) {
            // Skip materialisation if constraints aren't met, but still advance the date
            // so a single bad template doesn't wedge the whole schedule.
            log.warn("Failed to materialise recurring transaction {}: {}", recurring.getId(), e.getMessage());
        }
        recurring.setNextDueDate(advanceDate(recurring.getNextDueDate(), recurring.getFrequency()));
        recurringRepository.save(recurring);
    }

    private void createExpense(RecurringTransactionEntity recurring) {
        if (recurring.getCategory() == null || recurring.getAccount() == null) {
            return;
        }
        ExpenseEntity expense = new ExpenseEntity();
        expense.setUser(recurring.getUser());
        expense.setName(recurring.getName());
        expense.setDescription(recurring.getDescription());
        expense.setAmount(recurring.getAmount());
        expense.setExpenseDate(recurring.getNextDueDate());
        expense.setCategory(recurring.getCategory());
        expense.setAccount(recurring.getAccount());

        balanceService.applyExpense(recurring.getAccount(), recurring.getAmount());
        accountRepository.save(recurring.getAccount());
        expenseRepository.save(expense);
    }

    private void createIncome(RecurringTransactionEntity recurring) {
        if (recurring.getCategory() == null) {
            return;
        }
        IncomeEntity income = new IncomeEntity();
        income.setUser(recurring.getUser());
        income.setName(recurring.getName());
        income.setDescription(recurring.getDescription());
        income.setAmount(recurring.getAmount());
        income.setIncomeDate(recurring.getNextDueDate());
        income.setCategory(recurring.getCategory());

        if (recurring.getAccount() != null) {
            income.setAccount(recurring.getAccount());
            balanceService.applyIncome(recurring.getAccount(), recurring.getAmount());
            accountRepository.save(recurring.getAccount());
        }
        incomeRepository.save(income);
    }

    private void mapDtoToEntity(RecurringTransactionDTO dto, RecurringTransactionEntity entity, UUID userId) {
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
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
            if (!category.getUser().getId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid category");
            }
            entity.setCategory(category);
        } else {
            entity.setCategory(null);
        }

        if (dto.getAccountId() != null) {
            AccountEntity account = accountRepository.findById(dto.getAccountId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
            if (!account.getUser().getId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid account");
            }
            entity.setAccount(account);
        } else {
            entity.setAccount(null);
        }
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
