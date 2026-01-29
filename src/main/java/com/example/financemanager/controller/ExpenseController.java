package com.example.financemanager.controller;

import com.example.financemanager.entities.ExpenseEntity;
import com.example.financemanager.repositories.CategoryRepository;
import com.example.financemanager.repositories.ExpenseRepository;
import com.example.financemanager.repositories.UserRepository;
import com.example.financemanager.service.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/expenses")
public class ExpenseController {

    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    public ExpenseController(
            ExpenseRepository expenseRepository,
            UserRepository userRepository,
            CategoryRepository categoryRepository) {
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
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

        mapDtoToEntity(dto, expense, user.getUserId());
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
    }
}
