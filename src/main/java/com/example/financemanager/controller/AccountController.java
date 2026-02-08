package com.example.financemanager.controller;

import com.example.financemanager.dto.AccountDTO;
import com.example.financemanager.entities.AccountEntity;
import com.example.financemanager.repositories.AccountRepository;
import com.example.financemanager.repositories.ExpenseRepository;
import com.example.financemanager.repositories.IncomeRepository;
import com.example.financemanager.repositories.UserRepository;
import com.example.financemanager.service.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import com.example.financemanager.entities.ExpenseEntity;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;

    public AccountController(AccountRepository accountRepository, UserRepository userRepository,
            ExpenseRepository expenseRepository, IncomeRepository incomeRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
        this.incomeRepository = incomeRepository;
    }

    @GetMapping
    public List<AccountDTO> getAccounts(@AuthenticationPrincipal CustomUserDetails user) {
        return accountRepository.findByUserId(user.getUserId()).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @PostMapping
    public AccountDTO createAccount(@AuthenticationPrincipal CustomUserDetails user, @RequestBody AccountDTO dto) {
        AccountEntity account = new AccountEntity();
        account.setUser(userRepository.getReferenceById(user.getUserId()));
        mapDtoToEntity(dto, account);
        return convertToDTO(accountRepository.save(account));
    }

    @PutMapping("/{id}")
    public AccountDTO updateAccount(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id,
            @RequestBody AccountDTO dto) {
        AccountEntity account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        if (!account.getUser().getId().equals(user.getUserId())) {
            throw new RuntimeException("Unauthorized");
        }

        mapDtoToEntity(dto, account);
        return convertToDTO(accountRepository.save(account));
    }

    @PostMapping("/{id}/recalculate")
    @Transactional
    public AccountDTO recalculateBalance(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        AccountEntity account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        if (!account.getUser().getId().equals(user.getUserId())) {
            throw new RuntimeException("Unauthorized");
        }

        BigDecimal totalExpenses = expenseRepository.findByAccount_Id(id).stream()
                .map(e -> e.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalIncomes = incomeRepository.findByAccount_Id(id).stream()
                .map(i -> i.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal newBalance;
        if (account.getType() == AccountEntity.AccountType.CREDIT_CARD) {
            // For CC, balance = spent - paid
            // In our system, expenses increase CC balance (spent), incomes decrease it
            // (paid)
            newBalance = totalExpenses.subtract(totalIncomes);
        } else {
            // For Savings, balance = incomes - expenses
            newBalance = totalIncomes.subtract(totalExpenses);
        }

        account.setBalance(newBalance);
        return convertToDTO(accountRepository.save(account));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAccount(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        AccountEntity account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        if (!account.getUser().getId().equals(user.getUserId())) {
            throw new RuntimeException("Unauthorized");
        }

        accountRepository.delete(account);
        return ResponseEntity.ok().build();
    }

    private AccountDTO convertToDTO(AccountEntity entity) {
        AccountDTO dto = new AccountDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setType(entity.getType());
        dto.setBalance(entity.getBalance());
        dto.setBankName(entity.getBankName());
        dto.setBillingCycleStartDay(entity.getBillingCycleStartDay());
        dto.setBillDateDay(entity.getBillDateDay());
        dto.setDueDateDay(entity.getDueDateDay());

        if (entity.getType() == AccountEntity.AccountType.CREDIT_CARD) {
            Integer cycleStartDay = entity.getBillingCycleStartDay();
            if (cycleStartDay != null) {
                LocalDate today = LocalDate.now();
                LocalDate cycleStartDate;
                int day = cycleStartDay;

                // Handle valid days for months
                if (today.getDayOfMonth() < day) {
                    LocalDate lastMonth = today.minusMonths(1);
                    cycleStartDate = lastMonth.withDayOfMonth(Math.min(day, lastMonth.lengthOfMonth()));
                } else {
                    cycleStartDate = today.withDayOfMonth(Math.min(day, today.lengthOfMonth()));
                }

                List<ExpenseEntity> cycleExpenses = expenseRepository
                        .findByAccount_IdAndExpenseDateGreaterThanEqual(entity.getId(), cycleStartDate);
                BigDecimal currentCycleSpent = cycleExpenses.stream()
                        .map(ExpenseEntity::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                dto.setCurrentCycleSpent(currentCycleSpent);

                // Last Statement Balance = Total Balance - Current Cycle Spent
                // If total balance (total debt) is 1500 and 500 is from this cycle, then 1000
                // is from last statement
                BigDecimal lastStatementBalance = entity.getBalance().subtract(currentCycleSpent);
                dto.setLastStatementBalance(lastStatementBalance);

                // Paid if last statement balance is zero or less (meaning fully paid off)
                dto.setLastStatementPaid(lastStatementBalance.compareTo(BigDecimal.ZERO) <= 0);
            }
        }

        return dto;
    }

    private void mapDtoToEntity(AccountDTO dto, AccountEntity entity) {
        entity.setName(dto.getName());
        entity.setType(dto.getType());
        entity.setBalance(dto.getBalance());
        entity.setBankName(dto.getBankName());
        entity.setBillingCycleStartDay(dto.getBillingCycleStartDay());
        entity.setBillDateDay(dto.getBillDateDay());
        entity.setDueDateDay(dto.getDueDateDay());
    }
}
