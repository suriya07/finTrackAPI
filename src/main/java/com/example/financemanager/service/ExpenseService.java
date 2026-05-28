package com.example.financemanager.service;

import com.example.financemanager.dto.ExpenseDTO;
import com.example.financemanager.entities.AccountEntity;
import com.example.financemanager.entities.CategoryEntity;
import com.example.financemanager.entities.ExpenseEntity;
import com.example.financemanager.repositories.AccountRepository;
import com.example.financemanager.repositories.CategoryRepository;
import com.example.financemanager.repositories.ExpenseRepository;
import com.example.financemanager.repositories.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Owns all expense business logic, including the account-balance side effects.
 * Mutating operations are {@link Transactional} so the expense row and the
 * account balance are always committed together.
 */
@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AccountBalanceService balanceService;

    public ExpenseService(ExpenseRepository expenseRepository,
            CategoryRepository categoryRepository,
            AccountRepository accountRepository,
            UserRepository userRepository,
            AccountBalanceService balanceService) {
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.balanceService = balanceService;
    }

    @Transactional(readOnly = true)
    public List<ExpenseEntity> getExpenses(UUID userId, Integer month, Integer year,
            LocalDate fromDate, LocalDate toDate, UUID categoryId, UUID accountId, String search) {

        LocalDate start = fromDate;
        LocalDate end = toDate;

        if (start == null && end == null && month != null && year != null) {
            YearMonth yearMonth = YearMonth.of(year, month);
            start = yearMonth.atDay(1);
            end = yearMonth.atEndOfMonth();
        }

        Collection<UUID> categoryIds = resolveCategoryIds(categoryId);

        if (categoryIds == null && accountId == null && search == null
                && start != null && end != null) {
            return expenseRepository.findByUserIdAndExpenseDateBetweenOrderByExpenseDateDesc(userId, start, end);
        }

        if (start != null || end != null || categoryIds != null || accountId != null || search != null) {
            return expenseRepository.findFilteredExpenses(userId, start, end, categoryIds, accountId, search);
        }

        return expenseRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Page<ExpenseEntity> getExpensesPaged(UUID userId, Integer month, Integer year,
            LocalDate fromDate, LocalDate toDate, UUID categoryId, UUID accountId, String search,
            int page, int size) {

        LocalDate start = fromDate;
        LocalDate end = toDate;
        if (start == null && end == null && month != null && year != null) {
            YearMonth yearMonth = YearMonth.of(year, month);
            start = yearMonth.atDay(1);
            end = yearMonth.atEndOfMonth();
        }

        Collection<UUID> categoryIds = resolveCategoryIds(categoryId);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.DESC, "expense_date"));
        return expenseRepository.findFilteredExpensesPaged(userId, start, end, categoryIds, accountId, search, pageable);
    }

    @Transactional
    public ExpenseEntity create(UUID userId, ExpenseDTO dto) {
        ExpenseEntity expense = new ExpenseEntity();
        expense.setUser(userRepository.getReferenceById(userId));
        mapDtoToEntity(dto, expense, userId);

        balanceService.applyExpense(expense.getAccount(), expense.getAmount());
        accountRepository.save(expense.getAccount());
        return expenseRepository.save(expense);
    }

    @Transactional
    public ExpenseEntity update(UUID userId, UUID id, ExpenseDTO dto) {
        ExpenseEntity expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
        if (!expense.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized");
        }

        BigDecimal oldAmount = expense.getAmount();
        AccountEntity oldAccount = expense.getAccount();

        mapDtoToEntity(dto, expense, userId);

        AccountEntity newAccount = expense.getAccount();

        if (oldAccount != null) {
            balanceService.reverseExpense(oldAccount, oldAmount);
        }
        balanceService.applyExpense(newAccount, expense.getAmount());

        if (oldAccount != null) {
            accountRepository.save(oldAccount);
        }
        accountRepository.save(newAccount);
        return expenseRepository.save(expense);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        ExpenseEntity expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
        if (!expense.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized");
        }

        AccountEntity account = expense.getAccount();
        if (account != null) {
            balanceService.reverseExpense(account, expense.getAmount());
            accountRepository.save(account);
        }
        expenseRepository.delete(expense);
    }

    private Collection<UUID> resolveCategoryIds(UUID categoryId) {
        if (categoryId == null) {
            return null;
        }
        Collection<UUID> ids = new HashSet<>();
        ids.add(categoryId);
        categoryRepository.findById(categoryId)
                .ifPresent(category -> collectCategoryIdsRecursively(category, ids));
        return ids;
    }

    private void mapDtoToEntity(ExpenseDTO dto, ExpenseEntity expense, UUID userId) {
        expense.setName(dto.getName());
        expense.setDescription(dto.getDescription());
        expense.setAmount(dto.getAmount());
        expense.setExpenseDate(dto.getDate());

        if (dto.getCategoryId() != null) {
            CategoryEntity category = categoryRepository.findById(dto.getCategoryId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
            if (!category.getUser().getId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid category");
            }
            expense.setCategory(category);
        }

        if (dto.getAccountId() != null) {
            AccountEntity account = accountRepository.findById(dto.getAccountId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
            if (!account.getUser().getId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid account");
            }
            expense.setAccount(account);
        } else if (expense.getAccount() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account is mandatory for expenses");
        }
    }

    private void collectCategoryIdsRecursively(CategoryEntity category, Collection<UUID> ids) {
        if (category.getSubCategories() != null) {
            for (CategoryEntity sub : category.getSubCategories()) {
                ids.add(sub.getId());
                collectCategoryIdsRecursively(sub, ids);
            }
        }
    }
}
