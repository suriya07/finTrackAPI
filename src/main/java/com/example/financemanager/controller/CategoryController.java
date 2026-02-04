package com.example.financemanager.controller;

import com.example.financemanager.entities.CategoryEntity;
import com.example.financemanager.repositories.CategoryRepository;
import com.example.financemanager.repositories.BudgetRepository;
import com.example.financemanager.repositories.ExpenseRepository;
import com.example.financemanager.repositories.IncomeRepository;
import com.example.financemanager.repositories.UserRepository;
import com.example.financemanager.service.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final BudgetRepository budgetRepository;

    public CategoryController(CategoryRepository categoryRepository,
            UserRepository userRepository,
            ExpenseRepository expenseRepository,
            IncomeRepository incomeRepository,
            BudgetRepository budgetRepository) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
        this.incomeRepository = incomeRepository;
        this.budgetRepository = budgetRepository;
    }

    @GetMapping
    public List<CategoryEntity> getCategories(@AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) String type) {
        // Return only root categories to avoid duplication (subcategories are nested in
        // children)
        List<CategoryEntity> all = categoryRepository.findByUserId(user.getUserId());

        if (type != null && !type.isBlank()) {
            final String filterType = type.toUpperCase();
            return all.stream()
                    .filter(c -> c.getParent() == null && filterType.equals(c.getType()))
                    .toList();
        }

        return all.stream().filter(c -> c.getParent() == null).toList();
    }

    @PostMapping
    public CategoryEntity createCategory(@AuthenticationPrincipal CustomUserDetails user,
            @RequestBody com.example.financemanager.dto.CategoryDTO dto) {
        CategoryEntity category = new CategoryEntity();
        category.setName(dto.getName());
        category.setAllowedNestingDepth(dto.getAllowedNestingDepth());
        category.setUser(userRepository.getReferenceById(user.getUserId()));

        // Validate and set type
        if (dto.getType() == null || dto.getType().trim().isEmpty()) {
            throw new RuntimeException("Category type is required");
        }
        String type = dto.getType().toUpperCase();
        if (!type.equals("INCOME") && !type.equals("EXPENSE") && !type.equals("BUDGET")) {
            throw new RuntimeException("Invalid category type. Must be INCOME, EXPENSE, or BUDGET");
        }
        category.setType(type);

        if (dto.getParentId() != null) {
            CategoryEntity parent = categoryRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new RuntimeException("Parent category not found"));

            // Validate: Parent must belong to same user
            if (!parent.getUser().getId().equals(user.getUserId())) {
                throw new RuntimeException("Invalid parent category");
            }

            // Validate: Parent type must match child type
            if (!parent.getType().equals(type)) {
                throw new RuntimeException("Child category type must match parent category type");
            }

            // Validate: Nesting Limit
            if (parent.getAllowedNestingDepth() != null && parent.getAllowedNestingDepth() <= 0) {
                throw new RuntimeException("Parent category does not allow further nesting");
            }

            category.setParent(parent);

            // Decrease depth limit for child
            if (parent.getAllowedNestingDepth() != null) {
                category.setAllowedNestingDepth(parent.getAllowedNestingDepth() - 1);
            }
        }

        return categoryRepository.save(category);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCategory(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        CategoryEntity category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        if (!category.getUser().getId().equals(user.getUserId())) {
            throw new RuntimeException("Unauthorized to delete this category");
        }

        // Collect all categories in the hierarchy
        List<CategoryEntity> allCategories = new ArrayList<>();
        collectHierarchy(category, allCategories);

        // Check if any category in the hierarchy is in use
        for (CategoryEntity cat : allCategories) {
            if (expenseRepository.existsByCategoryId(cat.getId()) ||
                    incomeRepository.existsByCategoryId(cat.getId()) ||
                    budgetRepository.existsByCategoryId(cat.getId())) {
                return ResponseEntity.badRequest().body("Category \"" + cat.getName()
                        + "\" or one of its sub-categories is in use and cannot be deleted. " +
                        "Please reassign associated transactions/budgets first.");
            }
        }

        categoryRepository.delete(category);
        return ResponseEntity.ok().build();
    }

    private void collectHierarchy(CategoryEntity category, List<CategoryEntity> list) {
        list.add(category);
        for (CategoryEntity sub : category.getSubCategories()) {
            collectHierarchy(sub, list);
        }
    }
}
