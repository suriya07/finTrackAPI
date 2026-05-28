package com.example.financemanager.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** A budget alongside how much has actually been spent against it this month. */
public record BudgetStatusDTO(
        UUID budgetId,
        UUID categoryId,
        String categoryName,
        BigDecimal budgeted,
        BigDecimal spent,
        BigDecimal remaining,
        double percentUsed,
        boolean overBudget) {
}
