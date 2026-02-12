package com.example.financemanager.entities;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class AccountEntity extends BaseAuditableEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private UserEntity user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    @Column(nullable = false)
    private BigDecimal balance;

    private String bankName;

    // Credit Card specific fields
    private Integer billingCycleStartDay;
    private Integer billDateDay;
    private Integer dueDateDay;

    public AccountEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
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

    public enum AccountType {
        CREDIT_CARD, SAVINGS
    }
}
