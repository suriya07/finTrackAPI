package com.example.financemanager.controller;

import com.example.financemanager.entities.ExpenseEntity;
import com.example.financemanager.repositories.AccountRepository;
import com.example.financemanager.repositories.CategoryRepository;
import com.example.financemanager.repositories.ExpenseRepository;
import com.example.financemanager.repositories.UserRepository;
import com.example.financemanager.service.CustomUserDetails;
import com.example.financemanager.entities.AccountEntity;
import com.example.financemanager.dto.ExpenseFilterDTO;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/expenses")
public class ExpenseController {

    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    public ExpenseController(
            ExpenseRepository expenseRepository,
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            AccountRepository accountRepository) {
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
    }

    @GetMapping
    public List<ExpenseEntity> getExpenses(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) java.util.UUID categoryId,
            @RequestParam(required = false) java.util.UUID accountId,
            @RequestParam(required = false) String search) {

        LocalDate start = fromDate;
        LocalDate end = toDate;

        // If no explicit dates, use month boundaries as default context
        if (start == null && end == null && month != null && year != null) {
            YearMonth yearMonth = YearMonth.of(year, month);
            start = yearMonth.atDay(1);
            end = yearMonth.atEndOfMonth();
        }

        Collection<UUID> categoryIds = null;
        if (categoryId != null) {
            categoryIds = new HashSet<>();
            categoryIds.add(categoryId);
            com.example.financemanager.entities.CategoryEntity category = categoryRepository.findById(categoryId)
                    .orElse(null);
            if (category != null) {
                collectCategoryIdsRecursively(category, categoryIds);
            }
        }

        // Use proven stable methods for simple date/month queries
        if (categoryIds == null && accountId == null && search == null) {
            if (start != null && end != null) {
                return expenseRepository.findByUserIdAndExpenseDateBetweenOrderByExpenseDateDesc(user.getUserId(),
                        start,
                        end);
            }
        }

        // Use the comprehensive filter query if ANY filter (including
        // category/account/search) is active
        if (start != null || end != null || categoryIds != null || accountId != null || search != null) {
            return expenseRepository.findFilteredExpenses(user.getUserId(), start, end, categoryIds, accountId, search);
        }

        // Fallback to absolute everything (usually only for deep debugging or uncaught
        // cases)
        return expenseRepository.findByUserId(user.getUserId());
    }

    @PostMapping("/filter")
    public List<ExpenseEntity> getFilteredExpenses(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody ExpenseFilterDTO filter) {

        LocalDate start = filter.getFromDate();
        LocalDate end = filter.getToDate();

        // If no explicit dates, use month boundaries as default context
        if (start == null && end == null && filter.getMonth() != null && filter.getYear() != null) {
            YearMonth yearMonth = YearMonth.of(filter.getYear(), filter.getMonth());
            start = yearMonth.atDay(1);
            end = yearMonth.atEndOfMonth();
        }

        Collection<UUID> categoryIds = null;
        if (filter.getCategoryId() != null) {
            categoryIds = new HashSet<>();
            categoryIds.add(filter.getCategoryId());
            com.example.financemanager.entities.CategoryEntity category = categoryRepository
                    .findById(filter.getCategoryId())
                    .orElse(null);
            if (category != null) {
                collectCategoryIdsRecursively(category, categoryIds);
            }
        }

        // Use proven stable methods for simple date/month queries
        if (categoryIds == null && filter.getAccountId() == null && filter.getSearch() == null) {
            if (start != null && end != null) {
                return expenseRepository.findByUserIdAndExpenseDateBetweenOrderByExpenseDateDesc(user.getUserId(),
                        start,
                        end);
            }
        }

        // Use the comprehensive filter query if ANY filter (including
        // category/account/search) is active
        if (start != null || end != null || categoryIds != null || filter.getAccountId() != null
                || filter.getSearch() != null) {
            return expenseRepository.findFilteredExpenses(user.getUserId(), start, end, categoryIds,
                    filter.getAccountId(), filter.getSearch());
        }

        // Fallback to absolute everything
        return expenseRepository.findByUserId(user.getUserId());
    }

    @PostMapping
    public ExpenseEntity createExpense(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody com.example.financemanager.dto.ExpenseDTO dto) {
        ExpenseEntity expense = new ExpenseEntity();
        expense.setUser(userRepository.getReferenceById(user.getUserId()));

        mapDtoToEntity(dto, expense, user.getUserId());

        // Update Balance
        AccountEntity account = expense.getAccount();
        if (account.getType() == AccountEntity.AccountType.CREDIT_CARD) {
            account.setBalance(account.getBalance().add(expense.getAmount()));
        } else {
            account.setBalance(account.getBalance().subtract(expense.getAmount()));
        }
        accountRepository.save(account);

        return expenseRepository.save(expense);
    }

    @PutMapping("/{id}")
    public ExpenseEntity updateExpense(
            @PathVariable java.util.UUID id,
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody com.example.financemanager.dto.ExpenseDTO dto) {
        ExpenseEntity expense = expenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense not found"));

        if (!expense.getUser().getId().equals(user.getUserId())) {
            throw new RuntimeException("Unauthorized");
        }

        // Handle Balance Adjustment
        BigDecimal oldAmount = expense.getAmount();
        AccountEntity oldAccount = expense.getAccount();

        mapDtoToEntity(dto, expense, user.getUserId());

        AccountEntity newAccount = expense.getAccount();
        BigDecimal newAmount = expense.getAmount();

        if (oldAccount == null) {
            // Old record had no account, just update the new one
            if (newAccount.getType() == AccountEntity.AccountType.CREDIT_CARD) {
                newAccount.setBalance(newAccount.getBalance().add(newAmount));
            } else {
                newAccount.setBalance(newAccount.getBalance().subtract(newAmount));
            }
            accountRepository.save(newAccount);
        } else if (oldAccount.getId().equals(newAccount.getId())) {
            // Same account: adjust balance
            if (newAccount.getType() == AccountEntity.AccountType.CREDIT_CARD) {
                // For CC: subtract old, add new
                newAccount.setBalance(newAccount.getBalance().subtract(oldAmount).add(newAmount));
            } else {
                // For Savings: add old, subtract new
                newAccount.setBalance(newAccount.getBalance().add(oldAmount).subtract(newAmount));
            }
            accountRepository.save(newAccount);
        } else {
            // Different accounts
            if (oldAccount.getType() == AccountEntity.AccountType.CREDIT_CARD) {
                oldAccount.setBalance(oldAccount.getBalance().subtract(oldAmount));
            } else {
                oldAccount.setBalance(oldAccount.getBalance().add(oldAmount));
            }

            if (newAccount.getType() == AccountEntity.AccountType.CREDIT_CARD) {
                newAccount.setBalance(newAccount.getBalance().add(newAmount));
            } else {
                newAccount.setBalance(newAccount.getBalance().subtract(newAmount));
            }

            accountRepository.save(oldAccount);
            accountRepository.save(newAccount);
        }

        return expenseRepository.save(expense);
    }

    @DeleteMapping("/{id}")
    public void deleteExpense(
            @PathVariable java.util.UUID id,
            @AuthenticationPrincipal CustomUserDetails user) {
        ExpenseEntity expense = expenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense not found"));

        if (!expense.getUser().getId().equals(user.getUserId())) {
            throw new RuntimeException("Unauthorized");
        }

        // Update Balance
        AccountEntity account = expense.getAccount();
        if (account != null) {
            if (account.getType() == AccountEntity.AccountType.CREDIT_CARD) {
                account.setBalance(account.getBalance().subtract(expense.getAmount()));
            } else {
                account.setBalance(account.getBalance().add(expense.getAmount()));
            }
            accountRepository.save(account);
        }

        expenseRepository.delete(expense);
    }

    private void mapDtoToEntity(com.example.financemanager.dto.ExpenseDTO dto, ExpenseEntity expense,
            java.util.UUID userId) {
        expense.setName(dto.getName());
        expense.setDescription(dto.getDescription());
        expense.setAmount(dto.getAmount());
        expense.setExpenseDate(dto.getDate());

        if (dto.getCategoryId() != null) {
            com.example.financemanager.entities.CategoryEntity category = categoryRepository
                    .findById(dto.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found"));

            if (!category.getUser().getId().equals(userId)) {
                throw new RuntimeException("Invalid category");
            }
            expense.setCategory(category);
        }

        if (dto.getAccountId() != null) {
            com.example.financemanager.entities.AccountEntity account = accountRepository
                    .findById(dto.getAccountId())
                    .orElseThrow(() -> new RuntimeException("Account not found"));

            if (!account.getUser().getId().equals(userId)) {
                throw new RuntimeException("Invalid account");
            }
            expense.setAccount(account);
        } else {
            throw new RuntimeException("Account is mandatory for expenses");
        }
    }

    private void collectCategoryIdsRecursively(com.example.financemanager.entities.CategoryEntity category,
            Collection<UUID> ids) {
        if (category.getSubCategories() != null) {
            for (com.example.financemanager.entities.CategoryEntity sub : category.getSubCategories()) {
                ids.add(sub.getId());
                collectCategoryIdsRecursively(sub, ids);
            }
        }
    }
}
