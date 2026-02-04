package com.example.financemanager.dto;

import com.example.financemanager.entities.AccountEntity.AccountType;
import java.math.BigDecimal;
import java.util.UUID;

public class AccountDTO {
    private UUID id;
    private String name;
    private AccountType type;
    private BigDecimal balance;
    private String bankName;
    private Integer billingCycleStartDay;
    private Integer billDateDay;
    private Integer dueDateDay;

    public AccountDTO() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AccountType getType() {
        return type;
    }

    public void setType(AccountType type) {
        this.type = type;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public Integer getBillingCycleStartDay() {
        return billingCycleStartDay;
    }

    public void setBillingCycleStartDay(Integer billingCycleStartDay) {
        this.billingCycleStartDay = billingCycleStartDay;
    }

    public Integer getBillDateDay() {
        return billDateDay;
    }

    public void setBillDateDay(Integer billDateDay) {
        this.billDateDay = billDateDay;
    }

    public Integer getDueDateDay() {
        return dueDateDay;
    }

    public void setDueDateDay(Integer dueDateDay) {
        this.dueDateDay = dueDateDay;
    }
}
