package com.example.financemanager.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class BudgetDTO {
    private UUID id;
    private UUID categoryId;
    private BigDecimal amount;
    private LocalDate month; // Expected as yyyy-mm-01

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getMonth() {
        return month;
    }

    public void setMonth(LocalDate month) {
        this.month = month;
    }
}
