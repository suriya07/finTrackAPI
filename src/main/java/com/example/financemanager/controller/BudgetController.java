package com.example.financemanager.controller;

import com.example.financemanager.dto.BudgetDTO;
import com.example.financemanager.entities.BudgetEntity;
import com.example.financemanager.service.BudgetService;
import com.example.financemanager.service.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    public List<BudgetEntity> getBudgets(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        return budgetService.getBudgets(user.getUserId(), month, year);
    }

    @PostMapping
    public BudgetEntity createBudget(@AuthenticationPrincipal CustomUserDetails user, @RequestBody BudgetDTO dto) {
        return budgetService.create(user.getUserId(), dto);
    }

    @PutMapping("/{id}")
    public BudgetEntity updateBudget(@PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody BudgetDTO dto) {
        return budgetService.update(user.getUserId(), id, dto);
    }

    @DeleteMapping("/{id}")
    public void deleteBudget(@PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails user) {
        budgetService.delete(user.getUserId(), id);
    }
}
