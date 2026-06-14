package com.example.financemanager.service;

import com.example.financemanager.dto.ExpenseDTO;
import com.example.financemanager.entities.AccountEntity;
import com.example.financemanager.entities.ExpenseEntity;
import com.example.financemanager.entities.UserEntity;
import com.example.financemanager.repositories.AccountRepository;
import com.example.financemanager.repositories.CategoryRepository;
import com.example.financemanager.repositories.ExpenseRepository;
import com.example.financemanager.repositories.GroupRepository;
import com.example.financemanager.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that the transactional expense flow keeps the account balance in
 * lockstep with the expense being created, updated, or deleted.
 */
@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock private ExpenseRepository expenseRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private UserRepository userRepository;
    @Mock private ReceiptStorageService receiptStorage;

    private ExpenseService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ExpenseService(expenseRepository, categoryRepository, accountRepository,
                groupRepository, userRepository, new AccountBalanceService(), receiptStorage);
        lenient().when(expenseRepository.save(any(ExpenseEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private AccountEntity savingsAccount(String balance) {
        UserEntity owner = new UserEntity();
        owner.setId(userId);
        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setUser(owner);
        account.setType(AccountEntity.AccountType.SAVINGS);
        account.setBalance(new BigDecimal(balance));
        return account;
    }

    private ExpenseDTO dto(String amount) {
        ExpenseDTO dto = new ExpenseDTO();
        dto.setName("Groceries");
        dto.setAmount(new BigDecimal(amount));
        dto.setDate(LocalDate.now());
        dto.setAccountId(accountId);
        return dto;
    }

    @Test
    void createDeductsFromSavingsAndPersistsAccount() {
        AccountEntity account = savingsAccount("100");
        when(userRepository.getReferenceById(userId)).thenReturn(account.getUser());
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        ExpenseEntity created = service.create(userId, dto("40"));

        assertThat(account.getBalance()).isEqualByComparingTo("60");
        assertThat(created.getAmount()).isEqualByComparingTo("40");
        verify(accountRepository).save(account);
    }

    @Test
    void updateAppliesOnlyTheDelta() {
        AccountEntity account = savingsAccount("60"); // already reflects an existing -40 expense
        ExpenseEntity existing = new ExpenseEntity();
        existing.setUser(account.getUser());
        existing.setAccount(account);
        existing.setAmount(new BigDecimal("40"));

        when(expenseRepository.findById(any(UUID.class))).thenReturn(Optional.of(existing));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        service.update(userId, UUID.randomUUID(), dto("50"));

        // reverse old 40 (+40 -> 100), apply new 50 (-50 -> 50)
        assertThat(account.getBalance()).isEqualByComparingTo("50");
    }

    @Test
    void deleteReversesTheExpense() {
        AccountEntity account = savingsAccount("60");
        ExpenseEntity existing = new ExpenseEntity();
        existing.setUser(account.getUser());
        existing.setAccount(account);
        existing.setAmount(new BigDecimal("40"));

        when(expenseRepository.findById(any(UUID.class))).thenReturn(Optional.of(existing));

        service.delete(userId, UUID.randomUUID());

        assertThat(account.getBalance()).isEqualByComparingTo("100");
        verify(expenseRepository).delete(existing);
    }
}
