package com.example.financemanager.controller;

import com.example.financemanager.dto.IncomeDTO;
import com.example.financemanager.entities.CategoryEntity;
import com.example.financemanager.entities.IncomeEntity;
import com.example.financemanager.repositories.CategoryRepository;
import com.example.financemanager.repositories.IncomeRepository;
import com.example.financemanager.repositories.UserRepository;
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

    public IncomeController(IncomeRepository incomeRepository, CategoryRepository categoryRepository,
            UserRepository userRepository) {
        this.incomeRepository = incomeRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
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

        populateEntityFromDTO(income, dto, user.getUserId());
        return incomeRepository.save(income);
    }

    @DeleteMapping("/{id}")
    public void deleteIncome(@PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails user) {
        IncomeEntity income = incomeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Income not found"));

        if (!income.getUser().getId().equals(user.getUserId())) {
            throw new RuntimeException("Unauthorized to delete this income");
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
    }
}
