package com.example.financemanager.controller;

import com.example.financemanager.dto.DebtDTO;
import com.example.financemanager.entities.CategoryEntity;
import com.example.financemanager.entities.DebtEntity;
import com.example.financemanager.entities.ExpenseEntity;
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

    public DebtController(DebtRepository debtRepository, UserRepository userRepository,
            CategoryRepository categoryRepository, ExpenseRepository expenseRepository) {
        this.debtRepository = debtRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.expenseRepository = expenseRepository;
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
        debtRepository.save(debt);

        // Record as expense
        CategoryEntity category = categoryRepository.findByUserId(user.getUserId()).stream()
                .filter(c -> "Debt Repayment".equalsIgnoreCase(c.getName()))
                .findFirst()
                .orElseGet(() -> {
                    CategoryEntity newCat = new CategoryEntity();
                    newCat.setName("Debt Repayment");
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

    private void mapDtoToEntity(DebtDTO dto, DebtEntity debt) {
        debt.setName(dto.getName());
        debt.setAmount(dto.getAmount());
        debt.setInterest(dto.getInterest());
        debt.setDueDate(dto.getDueDate());
        debt.setEndDate(dto.getEndDate());
        debt.setTotalEmis(dto.getTotalEmis());
    }

    public static class DebtPaymentDTO {
        private BigDecimal amount;
        private LocalDate date;

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
    }
}
