package com.example.financemanager.controller;

import com.example.financemanager.dto.DebtDTO;
import com.example.financemanager.entities.CategoryEntity;
import com.example.financemanager.entities.DebtEntity;
import com.example.financemanager.entities.ExpenseEntity;
import com.example.financemanager.repositories.AccountRepository;
import com.example.financemanager.repositories.CategoryRepository;
import com.example.financemanager.repositories.DebtRepository;
import com.example.financemanager.repositories.ExpenseRepository;
import com.example.financemanager.repositories.UserRepository;
import com.example.financemanager.service.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/debts")
public class DebtController {

    private final DebtRepository debtRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;
    private final AccountRepository accountRepository;

    public DebtController(DebtRepository debtRepository, UserRepository userRepository,
            CategoryRepository categoryRepository, ExpenseRepository expenseRepository,
            AccountRepository accountRepository) {
        this.debtRepository = debtRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.expenseRepository = expenseRepository;
        this.accountRepository = accountRepository;
    }

    @GetMapping
    public List<DebtEntity> getDebts(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) Integer toMonth,
            @RequestParam(required = false) Integer toYear) {

        if (toMonth != null && toYear != null) {
            // End of the specified month
            LocalDateTime endOfMonth = LocalDateTime.of(toYear, toMonth, 1, 0, 0)
                    .plusMonths(1)
                    .minusNanos(1);
            Instant limit = endOfMonth.atZone(ZoneId.systemDefault()).toInstant();
            return debtRepository.findByUserIdAndCreatedAtBeforeOrderByCreatedAtDesc(user.getUserId(), limit);
        }

        return debtRepository.findByUserId(user.getUserId());
    }

    @PostMapping
    public DebtEntity createDebt(@AuthenticationPrincipal CustomUserDetails user, @RequestBody DebtDTO dto) {
        DebtEntity debt = new DebtEntity();
        debt.setUser(userRepository.getReferenceById(user.getUserId()));
        mapDtoToEntity(dto, debt);
        return debtRepository.save(debt);
    }

    @PutMapping("/{id}")
    public DebtEntity updateDebt(@PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody DebtDTO dto) {
        DebtEntity debt = debtRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Debt record not found"));

        if (!debt.getUser().getId().equals(user.getUserId())) {
            throw new RuntimeException("Unauthorized access to debt record");
        }

        mapDtoToEntity(dto, debt);
        return debtRepository.save(debt);
    }

    @PostMapping("/{id}/payments")
    @Transactional
    public DebtEntity recordPayment(@PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody DebtPaymentDTO paymentDto) {
        DebtEntity debt = debtRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Debt record not found"));

        if (!debt.getUser().getId().equals(user.getUserId())) {
            throw new RuntimeException("Unauthorized access to debt record");
        }

        BigDecimal paymentAmount = paymentDto.getAmount();
        debt.setAmount(debt.getAmount().subtract(paymentAmount));

        if (debt.getEmisPending() != null && debt.getEmisPending() > 0) {
            debt.setEmisPending(debt.getEmisPending() - 1);
        }

        debtRepository.save(debt);

        // Record as expense
        CategoryEntity category = categoryRepository.findByUserId(user.getUserId()).stream()
                .filter(c -> "Debt Repayment".equalsIgnoreCase(c.getName()))
                .findFirst()
                .orElseGet(() -> {
                    CategoryEntity newCat = new CategoryEntity();
                    newCat.setName("Debt Repayment");
                    newCat.setType("EXPENSE");
                    newCat.setUser(userRepository.getReferenceById(user.getUserId()));
                    newCat.setAllowedNestingDepth(0);
                    return categoryRepository.save(newCat);
                });

        ExpenseEntity expense = new ExpenseEntity();
        expense.setUser(userRepository.getReferenceById(user.getUserId()));
        expense.setCategory(category);
        expense.setName("Repayment: " + debt.getName());
        expense.setAmount(paymentAmount);
        expense.setExpenseDate(paymentDto.getDate() != null ? paymentDto.getDate() : LocalDate.now());
        expense.setDebt(debt); // Link expense to debt

        if (paymentDto.getAccountId() != null) {
            com.example.financemanager.entities.AccountEntity account = accountRepository
                    .findById(paymentDto.getAccountId())
                    .orElseThrow(() -> new RuntimeException("Account not found"));

            if (!account.getUser().getId().equals(user.getUserId())) {
                throw new RuntimeException("Invalid account");
            }
            expense.setAccount(account);

            // Update Account Balance
            if (account.getType() == com.example.financemanager.entities.AccountEntity.AccountType.CREDIT_CARD) {
                account.setBalance(account.getBalance().add(paymentAmount));
            } else {
                account.setBalance(account.getBalance().subtract(paymentAmount));
            }
            accountRepository.save(account);
        }

        expenseRepository.save(expense);

        return debt;
    }

    @GetMapping("/{id}/payments")
    public List<ExpenseEntity> getPayments(@PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails user) {
        DebtEntity debt = debtRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Debt record not found"));

        if (!debt.getUser().getId().equals(user.getUserId())) {
            throw new RuntimeException("Unauthorized access to debt record");
        }

        return expenseRepository.findByDebt_Id(id);
    }

    @DeleteMapping("/{id}")
    public void deleteDebt(@PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails user) {
        DebtEntity debt = debtRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Debt record not found"));

        if (!debt.getUser().getId().equals(user.getUserId())) {
            throw new RuntimeException("Unauthorized access to debt record");
        }

        debtRepository.delete(debt);
    }

    @PutMapping("/{id}/payments/{paymentId}")
    @Transactional
    public DebtEntity updatePayment(@PathVariable UUID id, @PathVariable UUID paymentId,
            @AuthenticationPrincipal CustomUserDetails user, @RequestBody DebtPaymentDTO paymentDto) {
        DebtEntity debt = debtRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Debt record not found"));

        ExpenseEntity payment = expenseRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment record not found"));

        if (!payment.getUser().getId().equals(user.getUserId()) || !payment.getDebt().getId().equals(id)) {
            throw new RuntimeException("Unauthorized or invalid payment record");
        }

        BigDecimal oldAmount = payment.getAmount();
        BigDecimal newAmount = paymentDto.getAmount();

        // Adjust debt balance
        debt.setAmount(debt.getAmount().add(oldAmount).subtract(newAmount));

        // Update payment record
        payment.setAmount(newAmount);
        payment.setExpenseDate(paymentDto.getDate() != null ? paymentDto.getDate() : LocalDate.now());

        // Handle Account Balance Adjustment if account is linked
        com.example.financemanager.entities.AccountEntity account = payment.getAccount();
        if (account != null) {
            BigDecimal diff = newAmount.subtract(oldAmount);
            if (account.getType() == com.example.financemanager.entities.AccountEntity.AccountType.CREDIT_CARD) {
                account.setBalance(account.getBalance().add(diff));
            } else {
                account.setBalance(account.getBalance().subtract(diff));
            }
            accountRepository.save(account);
        }

        expenseRepository.save(payment);
        return debtRepository.save(debt);
    }

    @DeleteMapping("/{id}/payments/{paymentId}")
    @Transactional
    public DebtEntity deletePayment(@PathVariable UUID id, @PathVariable UUID paymentId,
            @AuthenticationPrincipal CustomUserDetails user) {
        DebtEntity debt = debtRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Debt record not found"));

        ExpenseEntity payment = expenseRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment record not found"));

        if (!payment.getUser().getId().equals(user.getUserId()) || !payment.getDebt().getId().equals(id)) {
            throw new RuntimeException("Unauthorized or invalid payment record");
        }

        // Revert debt balance
        debt.setAmount(debt.getAmount().add(payment.getAmount()));

        // Increment EMIs pending if applicable
        if (debt.getEmisPending() != null && debt.getTotalEmis() != null) {
            debt.setEmisPending(debt.getEmisPending() + 1);
        }

        // Revert account balance if linked
        com.example.financemanager.entities.AccountEntity account = payment.getAccount();
        if (account != null) {
            if (account.getType() == com.example.financemanager.entities.AccountEntity.AccountType.CREDIT_CARD) {
                account.setBalance(account.getBalance().subtract(payment.getAmount()));
            } else {
                account.setBalance(account.getBalance().add(payment.getAmount()));
            }
            accountRepository.save(account);
        }

        expenseRepository.delete(payment);
        return debtRepository.save(debt);
    }

    private void mapDtoToEntity(DebtDTO dto, DebtEntity debt) {
        debt.setName(dto.getName());
        debt.setAmount(dto.getAmount());
        debt.setInterest(dto.getInterest());
        debt.setDueDate(dto.getDueDate());
        debt.setEndDate(dto.getEndDate());
        debt.setTotalEmis(dto.getTotalEmis());
        debt.setDescription(dto.getDescription());
        debt.setStartDate(dto.getStartDate());
        debt.setInitialAmount(dto.getInitialAmount());
        debt.setEmisPending(dto.getEmisPending());
        if (dto.getLoanType() != null) {
            debt.setLoanType(com.example.financemanager.entities.LoanType.valueOf(dto.getLoanType()));
        }
    }

    public static class DebtPaymentDTO {
        private BigDecimal amount;
        private LocalDate date;
        private UUID accountId;

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }

        public UUID getAccountId() {
            return accountId;
        }

        public void setAccountId(UUID accountId) {
            this.accountId = accountId;
        }
    }
}
