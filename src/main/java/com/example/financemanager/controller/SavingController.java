package com.example.financemanager.controller;

import com.example.financemanager.dto.SavingDTO;
import com.example.financemanager.entities.SavingEntity;
import com.example.financemanager.entities.CategoryEntity;
import com.example.financemanager.entities.ExpenseEntity;
import com.example.financemanager.repositories.CategoryRepository;
import com.example.financemanager.repositories.ExpenseRepository;
import com.example.financemanager.repositories.SavingRepository;
import com.example.financemanager.repositories.UserRepository;
import com.example.financemanager.service.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/savings")
public class SavingController {

    private final SavingRepository savingRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;

    public SavingController(SavingRepository savingRepository, UserRepository userRepository,
            CategoryRepository categoryRepository, ExpenseRepository expenseRepository) {
        this.savingRepository = savingRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.expenseRepository = expenseRepository;
    }

    @GetMapping
    public List<SavingEntity> getSavings(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) Integer toMonth,
            @RequestParam(required = false) Integer toYear) {

        if (toMonth != null && toYear != null) {
            LocalDateTime endOfMonth = LocalDateTime.of(toYear, toMonth, 1, 0, 0)
                    .plusMonths(1)
                    .minusNanos(1);
            Instant limit = endOfMonth.atZone(ZoneId.systemDefault()).toInstant();
            return savingRepository.findByUserIdAndCreatedAtBeforeOrderByCreatedAtDesc(user.getUserId(), limit);
        }

        return savingRepository.findByUserId(user.getUserId());
    }

    @PostMapping
    public SavingEntity createSaving(@AuthenticationPrincipal CustomUserDetails user, @RequestBody SavingDTO dto) {
        SavingEntity saving = new SavingEntity();
        saving.setUser(userRepository.getReferenceById(user.getUserId()));
        mapDtoToEntity(dto, saving);
        return savingRepository.save(saving);
    }

    @PutMapping("/{id}")
    public SavingEntity updateSaving(@PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody SavingDTO dto) {
        SavingEntity saving = savingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Saving record not found"));

        if (!saving.getUser().getId().equals(user.getUserId())) {
            throw new RuntimeException("Unauthorized access to saving record");
        }

        mapDtoToEntity(dto, saving);
        return savingRepository.save(saving);
    }

    @DeleteMapping("/{id}")
    public void deleteSaving(@PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails user) {
        SavingEntity saving = savingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Saving record not found"));

        if (!saving.getUser().getId().equals(user.getUserId())) {
            throw new RuntimeException("Unauthorized access to saving record");
        }

        savingRepository.delete(saving);
    }

    @PostMapping("/{id}/contributions")
    @Transactional
    public SavingEntity recordContribution(@PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody ContributionDTO contributionDto) {
        SavingEntity saving = savingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Saving record not found"));

        if (!saving.getUser().getId().equals(user.getUserId())) {
            throw new RuntimeException("Unauthorized access to saving record");
        }

        BigDecimal contributionAmount = contributionDto.getAmount();
        saving.setAmount(saving.getAmount().add(contributionAmount));
        savingRepository.save(saving);

        // Record as expense (Savings Contribution)
        CategoryEntity category = categoryRepository.findByUserId(user.getUserId()).stream()
                .filter(c -> "Savings".equalsIgnoreCase(c.getName()))
                .findFirst()
                .orElseGet(() -> {
                    CategoryEntity newCat = new CategoryEntity();
                    newCat.setName("Savings");
                    newCat.setUser(userRepository.getReferenceById(user.getUserId()));
                    newCat.setAllowedNestingDepth(0);
                    return categoryRepository.save(newCat);
                });

        ExpenseEntity expense = new ExpenseEntity();
        expense.setUser(userRepository.getReferenceById(user.getUserId()));
        expense.setCategory(category);
        expense.setName("Saving: " + saving.getName());
        expense.setAmount(contributionAmount);
        expense.setExpenseDate(contributionDto.getDate() != null ? contributionDto.getDate() : LocalDate.now());
        expense.setSaving(saving);
        expenseRepository.save(expense);

        return saving;
    }

    @GetMapping("/{id}/history")
    public List<ExpenseEntity> getHistory(@PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails user) {
        SavingEntity saving = savingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Saving record not found"));

        if (!saving.getUser().getId().equals(user.getUserId())) {
            throw new RuntimeException("Unauthorized access to saving record");
        }

        return expenseRepository.findBySaving_Id(id);
    }

    private void mapDtoToEntity(SavingDTO dto, SavingEntity saving) {
        saving.setName(dto.getName());
        saving.setCategory(dto.getCategory());
        saving.setAmount(dto.getAmount());
        saving.setTarget(dto.getTarget());
        saving.setSavingDate(dto.getDate());
    }

    public static class ContributionDTO {
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
