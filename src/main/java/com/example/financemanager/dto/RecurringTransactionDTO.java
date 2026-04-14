package com.example.financemanager.dto;

import com.example.financemanager.entities.RecurringTransactionEntity.RecurrenceFrequency;
import com.example.financemanager.entities.RecurringTransactionEntity.RecurringType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class RecurringTransactionDTO {

    private String name;
    private String description;
    private BigDecimal amount;
    private RecurrenceFrequency frequency;
    private RecurringType type;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean isActive = true;
    private UUID categoryId;
    private UUID accountId;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public RecurrenceFrequency getFrequency() { return frequency; }
    public void setFrequency(RecurrenceFrequency frequency) { this.frequency = frequency; }

    public RecurringType getType() { return type; }
    public void setType(RecurringType type) { this.type = type; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public UUID getCategoryId() { return categoryId; }
    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }

    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }
}
