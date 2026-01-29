package com.example.financemanager.controller;

import com.example.financemanager.dto.BudgetDTO;
import com.example.financemanager.entities.BudgetEntity;
import com.example.financemanager.entities.CategoryEntity;
import com.example.financemanager.repositories.BudgetRepository;
import com.example.financemanager.repositories.CategoryRepository;
import com.example.financemanager.repositories.UserRepository;
import com.example.financemanager.service.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/budgets")
public class BudgetController {

    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    public BudgetController(BudgetRepository budgetRepository, UserRepository userRepository,
            CategoryRepository categoryRepository) {
        this.budgetRepository = budgetRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public List<BudgetEntity> getBudgets(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {

        List<BudgetEntity> allBudgets = budgetRepository.findByUserId(user.getUserId());

        if (month != null && year != null) {
            return allBudgets.stream()
                    .filter(b -> b.getMonth().getMonthValue() == month && b.getMonth().getYear() == year)
                    .collect(Collectors.toList());
        }

        return allBudgets;
    }

    @PostMapping
    public BudgetEntity createBudget(@AuthenticationPrincipal CustomUserDetails user, @RequestBody BudgetDTO dto) {
        // Logic to prevent duplicate budgets for same month/category
        if (budgetRepository.existsByUserIdAndCategoryIdAndMonth(user.getUserId(), dto.getCategoryId(),
                dto.getMonth())) {
            throw new RuntimeException("Budget already exists for this category and month");
        }

        BudgetEntity budget = new BudgetEntity();
        budget.setUser(userRepository.getReferenceById(user.getUserId()));

        CategoryEntity category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found"));

        if (!category.getUser().getId().equals(user.getUserId())) {
            throw new RuntimeException("Invalid category selected");
        }

        budget.setCategory(category);
        budget.setAmount(dto.getAmount());
        budget.setMonth(dto.getMonth());

        return budgetRepository.save(budget);
    }

    @PutMapping("/{id}")
    public BudgetEntity updateBudget(@PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody BudgetDTO dto) {
        BudgetEntity budget = budgetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Budget not found"));

        if (!budget.getUser().getId().equals(user.getUserId())) {
            throw new RuntimeException("Unauthorized access to budget");
        }

        budget.setAmount(dto.getAmount());
        // Note: Month and Category update usually shouldn't happen via PUT for a
        // specific budget ID in this design,
        // but if needed, validation is required. For now, just amount is fine.

        return budgetRepository.save(budget);
    }

    @DeleteMapping("/{id}")
    public void deleteBudget(@PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails user) {
        BudgetEntity budget = budgetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Budget not found"));

        if (!budget.getUser().getId().equals(user.getUserId())) {
            throw new RuntimeException("Unauthorized access to budget");
        }

        budgetRepository.delete(budget);
    }
}
