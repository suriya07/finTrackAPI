package com.example.financemanager.service;

import com.example.financemanager.dto.BudgetDTO;
import com.example.financemanager.entities.BudgetEntity;
import com.example.financemanager.entities.CategoryEntity;
import com.example.financemanager.repositories.BudgetRepository;
import com.example.financemanager.repositories.CategoryRepository;
import com.example.financemanager.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    public BudgetService(BudgetRepository budgetRepository, UserRepository userRepository,
            CategoryRepository categoryRepository) {
        this.budgetRepository = budgetRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<BudgetEntity> getBudgets(UUID userId, Integer month, Integer year) {
        List<BudgetEntity> allBudgets = budgetRepository.findByUserId(userId);
        if (month != null && year != null) {
            return allBudgets.stream()
                    .filter(b -> b.getMonth().getMonthValue() == month && b.getMonth().getYear() == year)
                    .collect(Collectors.toList());
        }
        return allBudgets;
    }

    @Transactional
    public BudgetEntity create(UUID userId, BudgetDTO dto) {
        if (budgetRepository.existsByUserIdAndCategory_IdAndMonth(userId, dto.getCategoryId(), dto.getMonth())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Budget already exists for this category and month");
        }

        CategoryEntity category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        if (!category.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid category selected");
        }

        BudgetEntity budget = new BudgetEntity();
        budget.setUser(userRepository.getReferenceById(userId));
        budget.setCategory(category);
        budget.setAmount(dto.getAmount());
        budget.setMonth(dto.getMonth());
        return budgetRepository.save(budget);
    }

    @Transactional
    public BudgetEntity update(UUID userId, UUID id, BudgetDTO dto) {
        BudgetEntity budget = requireOwnedBudget(userId, id);
        // Only the amount is updatable for a given category/month budget.
        budget.setAmount(dto.getAmount());
        return budgetRepository.save(budget);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        BudgetEntity budget = requireOwnedBudget(userId, id);
        budgetRepository.delete(budget);
    }

    private BudgetEntity requireOwnedBudget(UUID userId, UUID id) {
        BudgetEntity budget = budgetRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found"));
        if (!budget.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized access to budget");
        }
        return budget;
    }
}
