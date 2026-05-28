package com.example.financemanager.dto;

import java.math.BigDecimal;

/** One month's income/expense totals, used to plot trends. */
public record TrendPointDTO(
        int year,
        int month,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal net) {
}
