package com.example.financemanager.controller;

import com.example.financemanager.entities.ExpenseEntity;
import com.example.financemanager.repositories.AccountRepository;
import com.example.financemanager.repositories.CategoryRepository;
import com.example.financemanager.repositories.ExpenseRepository;
import com.example.financemanager.repositories.UserRepository;
import com.example.financemanager.service.CustomUserDetails;
import com.example.financemanager.entities.AccountEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

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
            @RequestParam(required = false) Integer year) {

        if (month != null && year != null) {
            YearMonth yearMonth = YearMonth.of(year, month);
            LocalDate start = yearMonth.atDay(1);
            LocalDate end = yearMonth.atEndOfMonth();
            return expenseRepository.findByUserIdAndExpenseDateBetweenOrderByExpenseDateDesc(user.getUserId(), start,
                    end);
        }

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
}
