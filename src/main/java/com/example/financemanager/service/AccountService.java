package com.example.financemanager.service;

import com.example.financemanager.dto.AccountDTO;
import com.example.financemanager.entities.AccountEntity;
import com.example.financemanager.entities.ExpenseEntity;
import com.example.financemanager.repositories.AccountRepository;
import com.example.financemanager.repositories.ExpenseRepository;
import com.example.financemanager.repositories.IncomeRepository;
import com.example.financemanager.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;

    public AccountService(AccountRepository accountRepository, UserRepository userRepository,
            ExpenseRepository expenseRepository, IncomeRepository incomeRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
        this.incomeRepository = incomeRepository;
    }

    @Transactional(readOnly = true)
    public List<AccountDTO> getAccounts(UUID userId) {
        return accountRepository.findByUserId(userId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public AccountDTO create(UUID userId, AccountDTO dto) {
        AccountEntity account = new AccountEntity();
        account.setUser(userRepository.getReferenceById(userId));
        mapDtoToEntity(dto, account);
        return convertToDTO(accountRepository.save(account));
    }

    @Transactional
    public AccountDTO update(UUID userId, UUID id, AccountDTO dto) {
        AccountEntity account = requireOwnedAccount(userId, id);
        mapDtoToEntity(dto, account);
        return convertToDTO(accountRepository.save(account));
    }

    @Transactional
    public AccountDTO recalculateBalance(UUID userId, UUID id) {
        AccountEntity account = requireOwnedAccount(userId, id);

        BigDecimal totalExpenses = expenseRepository.findByAccount_Id(id).stream()
                .map(ExpenseEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalIncomes = incomeRepository.findByAccount_Id(id).stream()
                .map(i -> i.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal newBalance;
        if (account.getType() == AccountEntity.AccountType.CREDIT_CARD) {
            // Expenses increase CC balance (spent), incomes decrease it (paid).
            newBalance = totalExpenses.subtract(totalIncomes);
        } else {
            // Savings: incomes increase, expenses decrease.
            newBalance = totalIncomes.subtract(totalExpenses);
        }

        account.setBalance(newBalance);
        return convertToDTO(accountRepository.save(account));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        AccountEntity account = requireOwnedAccount(userId, id);
        accountRepository.delete(account);
    }

    private AccountEntity requireOwnedAccount(UUID userId, UUID id) {
        AccountEntity account = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (!account.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized");
        }
        return account;
    }

    private AccountDTO convertToDTO(AccountEntity entity) {
        AccountDTO dto = new AccountDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setType(entity.getType());
        dto.setBalance(entity.getBalance());
        dto.setBankName(entity.getBankName());
        dto.setBillingCycleStartDay(entity.getBillingCycleStartDay());
        dto.setBillDateDay(entity.getBillDateDay());
        dto.setDueDateDay(entity.getDueDateDay());

        if (entity.getType() == AccountEntity.AccountType.CREDIT_CARD) {
            Integer cycleStartDay = entity.getBillingCycleStartDay();
            if (cycleStartDay != null) {
                LocalDate today = LocalDate.now();
                LocalDate cycleStartDate;
                int day = cycleStartDay;

                if (today.getDayOfMonth() < day) {
                    LocalDate lastMonth = today.minusMonths(1);
                    cycleStartDate = lastMonth.withDayOfMonth(Math.min(day, lastMonth.lengthOfMonth()));
                } else {
                    cycleStartDate = today.withDayOfMonth(Math.min(day, today.lengthOfMonth()));
                }

                List<ExpenseEntity> cycleExpenses = expenseRepository
                        .findByAccount_IdAndExpenseDateGreaterThanEqual(entity.getId(), cycleStartDate);
                BigDecimal currentCycleSpent = cycleExpenses.stream()
                        .map(ExpenseEntity::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                dto.setCurrentCycleSpent(currentCycleSpent);

                BigDecimal lastStatementBalance = entity.getBalance().subtract(currentCycleSpent);
                dto.setLastStatementBalance(lastStatementBalance);
                dto.setLastStatementPaid(lastStatementBalance.compareTo(BigDecimal.ZERO) <= 0);
            }
        }

        return dto;
    }

    private void mapDtoToEntity(AccountDTO dto, AccountEntity entity) {
        entity.setName(dto.getName());
        entity.setType(dto.getType());
        entity.setBalance(dto.getBalance());
        entity.setBankName(dto.getBankName());
        entity.setBillingCycleStartDay(dto.getBillingCycleStartDay());
        entity.setBillDateDay(dto.getBillDateDay());
        entity.setDueDateDay(dto.getDueDateDay());
    }
}
