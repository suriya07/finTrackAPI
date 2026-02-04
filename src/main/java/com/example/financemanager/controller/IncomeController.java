package com.example.financemanager.controller;

import java.math.BigDecimal;

import com.example.financemanager.dto.IncomeDTO;
import com.example.financemanager.entities.CategoryEntity;
import com.example.financemanager.entities.IncomeEntity;
import com.example.financemanager.repositories.AccountRepository;
import com.example.financemanager.repositories.CategoryRepository;
import com.example.financemanager.repositories.IncomeRepository;
import com.example.financemanager.repositories.UserRepository;
import com.example.financemanager.entities.AccountEntity;
import com.example.financemanager.service.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/incomes")
public class IncomeController {

    private final IncomeRepository incomeRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    public IncomeController(IncomeRepository incomeRepository, CategoryRepository categoryRepository,
            UserRepository userRepository, AccountRepository accountRepository) {
        this.incomeRepository = incomeRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
    }

    @GetMapping
    public List<IncomeEntity> getIncomes(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {

        if (month != null && year != null) {
            YearMonth yearMonth = YearMonth.of(year, month);
            LocalDate start = yearMonth.atDay(1);
            LocalDate end = yearMonth.atEndOfMonth();
            return incomeRepository.findByUserIdAndIncomeDateBetweenOrderByIncomeDateDesc(user.getUserId(), start, end);
        }

        return incomeRepository.findByUserIdOrderByIncomeDateDesc(user.getUserId());
    }

    @PostMapping
    public IncomeEntity createIncome(@AuthenticationPrincipal CustomUserDetails user, @RequestBody IncomeDTO dto) {
        IncomeEntity income = new IncomeEntity();
        income.setUser(userRepository.getReferenceById(user.getUserId()));
        populateEntityFromDTO(income, dto, user.getUserId());

        // Update Account Balance
        AccountEntity account = income.getAccount();
        if (account.getType() == AccountEntity.AccountType.CREDIT_CARD) {
            account.setBalance(account.getBalance().subtract(income.getAmount()));
        } else {
            account.setBalance(account.getBalance().add(income.getAmount()));
        }
        accountRepository.save(account);

        return incomeRepository.save(income);
    }

    @PutMapping("/{id}")
    public IncomeEntity updateIncome(@PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody IncomeDTO dto) {
        IncomeEntity income = incomeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Income not found"));

        if (!income.getUser().getId().equals(user.getUserId())) {
            throw new RuntimeException("Unauthorized to update this income");
        }

        // Handle Balance Adjustment
        BigDecimal oldAmount = income.getAmount();
        AccountEntity oldAccount = income.getAccount();

        populateEntityFromDTO(income, dto, user.getUserId());

        AccountEntity newAccount = income.getAccount();
        BigDecimal newAmount = income.getAmount();

        if (oldAccount == null) {
            // Old record had no account, just add to the new one
            if (newAccount.getType() == AccountEntity.AccountType.CREDIT_CARD) {
                newAccount.setBalance(newAccount.getBalance().subtract(newAmount));
            } else {
                newAccount.setBalance(newAccount.getBalance().add(newAmount));
            }
            accountRepository.save(newAccount);
        } else if (oldAccount.getId().equals(newAccount.getId())) {
            // Same account, adjust balance
            if (newAccount.getType() == AccountEntity.AccountType.CREDIT_CARD) {
                // For CC: add old, subtract new
                newAccount.setBalance(newAccount.getBalance().add(oldAmount).subtract(newAmount));
            } else {
                // For Savings: subtract old, add new
                newAccount.setBalance(newAccount.getBalance().subtract(oldAmount).add(newAmount));
            }
            accountRepository.save(newAccount);
        } else {
            // Different accounts
            if (oldAccount.getType() == AccountEntity.AccountType.CREDIT_CARD) {
                oldAccount.setBalance(oldAccount.getBalance().add(oldAmount));
            } else {
                oldAccount.setBalance(oldAccount.getBalance().subtract(oldAmount));
            }

            if (newAccount.getType() == AccountEntity.AccountType.CREDIT_CARD) {
                newAccount.setBalance(newAccount.getBalance().subtract(newAmount));
            } else {
                newAccount.setBalance(newAccount.getBalance().add(newAmount));
            }

            accountRepository.save(oldAccount);
            accountRepository.save(newAccount);
        }

        return incomeRepository.save(income);
    }

    @DeleteMapping("/{id}")
    public void deleteIncome(@PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails user) {
        IncomeEntity income = incomeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Income not found"));

        if (!income.getUser().getId().equals(user.getUserId())) {
            throw new RuntimeException("Unauthorized to delete this income");
        }

        // Update Balance
        AccountEntity account = income.getAccount();
        if (account != null) {
            if (account.getType() == AccountEntity.AccountType.CREDIT_CARD) {
                account.setBalance(account.getBalance().add(income.getAmount()));
            } else {
                account.setBalance(account.getBalance().subtract(income.getAmount()));
            }
            accountRepository.save(account);
        }

        incomeRepository.delete(income);
    }

    private void populateEntityFromDTO(IncomeEntity entity, IncomeDTO dto, UUID userId) {
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setAmount(dto.getAmount());
        entity.setIncomeDate(dto.getDate());

        if (dto.getCategoryId() != null) {
            CategoryEntity category = categoryRepository.findById(dto.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found"));

            if (!category.getUser().getId().equals(userId)) {
                throw new RuntimeException("Invalid category");
            }

            // Ensure category is of type INCOME
            if (!"INCOME".equals(category.getType())) {
                throw new RuntimeException("Category must be of type INCOME");
            }

            entity.setCategory(category);
        }

        if (dto.getAccountId() != null) {
            AccountEntity account = accountRepository.findById(dto.getAccountId())
                    .orElseThrow(() -> new RuntimeException("Account not found"));

            if (!account.getUser().getId().equals(userId)) {
                throw new RuntimeException("Invalid account");
            }
            entity.setAccount(account);
        } else if (entity.getAccount() == null) {
            throw new RuntimeException("Account is mandatory for incomes");
        }
    }
}
