package com.example.financemanager.service;

import com.example.financemanager.entities.AccountEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Centralizes how transactions affect an account's balance.
 *
 * <p>The sign of the effect depends on the account type:
 * <ul>
 *   <li>CREDIT_CARD: an expense increases the balance (outstanding debt) and an
 *       income (payment) decreases it.</li>
 *   <li>SAVINGS: an expense decreases the balance and an income increases it.</li>
 * </ul>
 *
 * Keeping this in one place means create/update/delete flows can never drift
 * apart, and update becomes a simple "reverse the old, apply the new".
 */
@Service
public class AccountBalanceService {

    /** Signed amount added to the balance when an expense exists on this account. */
    public BigDecimal expenseDelta(AccountEntity account, BigDecimal amount) {
        return account.getType() == AccountEntity.AccountType.CREDIT_CARD ? amount : amount.negate();
    }

    /** Signed amount added to the balance when an income exists on this account. */
    public BigDecimal incomeDelta(AccountEntity account, BigDecimal amount) {
        return account.getType() == AccountEntity.AccountType.CREDIT_CARD ? amount.negate() : amount;
    }

    public void applyExpense(AccountEntity account, BigDecimal amount) {
        account.setBalance(account.getBalance().add(expenseDelta(account, amount)));
    }

    public void reverseExpense(AccountEntity account, BigDecimal amount) {
        account.setBalance(account.getBalance().subtract(expenseDelta(account, amount)));
    }

    public void applyIncome(AccountEntity account, BigDecimal amount) {
        account.setBalance(account.getBalance().add(incomeDelta(account, amount)));
    }

    public void reverseIncome(AccountEntity account, BigDecimal amount) {
        account.setBalance(account.getBalance().subtract(incomeDelta(account, amount)));
    }
}
