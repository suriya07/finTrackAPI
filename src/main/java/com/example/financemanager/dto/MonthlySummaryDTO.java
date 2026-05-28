package com.example.financemanager.dto;

import java.math.BigDecimal;

/** Headline figures for a single month. */
public record MonthlySummaryDTO(
        int year,
        int month,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal net,
        BigDecimal totalBudget,
        BigDecimal budgetRemaining) {
}
