package com.example.financemanager.service;

import com.example.financemanager.entities.AccountEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for the balance arithmetic — no Spring context, no database.
 * These pin down the credit-card vs savings sign conventions and the
 * apply/reverse round-trip invariant that the whole system relies on.
 */
class AccountBalanceServiceTest {

    private final AccountBalanceService service = new AccountBalanceService();

    private AccountEntity account(AccountEntity.AccountType type, String balance) {
        AccountEntity account = new AccountEntity();
        account.setType(type);
        account.setBalance(new BigDecimal(balance));
        return account;
    }

    @Test
    void expenseIncreasesCreditCardBalance() {
        AccountEntity cc = account(AccountEntity.AccountType.CREDIT_CARD, "100");
        service.applyExpense(cc, new BigDecimal("40"));
        assertThat(cc.getBalance()).isEqualByComparingTo("140");
    }

    @Test
    void expenseDecreasesSavingsBalance() {
        AccountEntity savings = account(AccountEntity.AccountType.SAVINGS, "100");
        service.applyExpense(savings, new BigDecimal("40"));
        assertThat(savings.getBalance()).isEqualByComparingTo("60");
    }

    @Test
    void incomeDecreasesCreditCardBalance() {
        AccountEntity cc = account(AccountEntity.AccountType.CREDIT_CARD, "100");
        service.applyIncome(cc, new BigDecimal("30"));
        assertThat(cc.getBalance()).isEqualByComparingTo("70");
    }

    @Test
    void incomeIncreasesSavingsBalance() {
        AccountEntity savings = account(AccountEntity.AccountType.SAVINGS, "100");
        service.applyIncome(savings, new BigDecimal("30"));
        assertThat(savings.getBalance()).isEqualByComparingTo("130");
    }

    @Test
    void reverseUndoesApplyForEveryCombination() {
        for (AccountEntity.AccountType type : AccountEntity.AccountType.values()) {
            AccountEntity acc = account(type, "250.55");

            service.applyExpense(acc, new BigDecimal("99.99"));
            service.reverseExpense(acc, new BigDecimal("99.99"));
            assertThat(acc.getBalance()).isEqualByComparingTo("250.55");

            service.applyIncome(acc, new BigDecimal("12.34"));
            service.reverseIncome(acc, new BigDecimal("12.34"));
            assertThat(acc.getBalance()).isEqualByComparingTo("250.55");
        }
    }
}
