package com.example.financemanager.service;

import com.example.financemanager.dto.IncomeDTO;
import com.example.financemanager.entities.AccountEntity;
import com.example.financemanager.entities.CategoryEntity;
import com.example.financemanager.entities.IncomeEntity;
import com.example.financemanager.repositories.AccountRepository;
import com.example.financemanager.repositories.CategoryRepository;
import com.example.financemanager.repositories.IncomeRepository;
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
 * Owns all income business logic, including account-balance side effects.
 * Mutating operations are {@link Transactional} so the income row and the
 * account balance are always committed together.
 */
@Service
public class IncomeService {

    private final IncomeRepository incomeRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AccountBalanceService balanceService;

    public IncomeService(IncomeRepository incomeRepository,
            CategoryRepository categoryRepository,
            AccountRepository accountRepository,
            UserRepository userRepository,
            AccountBalanceService balanceService) {
        this.incomeRepository = incomeRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.balanceService = balanceService;
    }

    @Transactional(readOnly = true)
    public List<IncomeEntity> getIncomes(UUID userId, Integer month, Integer year,
            LocalDate fromDate, LocalDate toDate, UUID categoryId, UUID accountId, String search) {

        LocalDate start = fromDate;
        LocalDate end = toDate;

        if (start == null && end == null && month != null && year != null) {
            YearMonth yearMonth = YearMonth.of(year, month);
            start = yearMonth.atDay(1);
            end = yearMonth.atEndOfMonth();
        }

        Collection<UUID> categoryIds = resolveCategoryIds(categoryId);

        if (accountId == null && categoryIds == null && search == null
                && start != null && end != null) {
            return incomeRepository.findByUserIdAndIncomeDateBetweenOrderByIncomeDateDesc(userId, start, end);
        }

        if (start != null || end != null || accountId != null || categoryIds != null || search != null) {
            return incomeRepository.findFilteredIncomes(userId, start, end, accountId, categoryIds, search);
        }

        return incomeRepository.findByUserIdOrderByIncomeDateDesc(userId);
    }

    @Transactional(readOnly = true)
    public Page<IncomeEntity> getIncomesPaged(UUID userId, Integer month, Integer year,
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
                Sort.by(Sort.Direction.DESC, "income_date"));
        return incomeRepository.findFilteredIncomesPaged(userId, start, end, accountId, categoryIds, search, pageable);
    }

    @Transactional
    public IncomeEntity create(UUID userId, IncomeDTO dto) {
        IncomeEntity income = new IncomeEntity();
        income.setUser(userRepository.getReferenceById(userId));
        populateEntityFromDTO(income, dto, userId);

        balanceService.applyIncome(income.getAccount(), income.getAmount());
        accountRepository.save(income.getAccount());
        return incomeRepository.save(income);
    }

    @Transactional
    public IncomeEntity update(UUID userId, UUID id, IncomeDTO dto) {
        IncomeEntity income = incomeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Income not found"));
        if (!income.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized to update this income");
        }

        BigDecimal oldAmount = income.getAmount();
        AccountEntity oldAccount = income.getAccount();

        populateEntityFromDTO(income, dto, userId);

        AccountEntity newAccount = income.getAccount();

        if (oldAccount != null) {
            balanceService.reverseIncome(oldAccount, oldAmount);
        }
        balanceService.applyIncome(newAccount, income.getAmount());

        if (oldAccount != null) {
            accountRepository.save(oldAccount);
        }
        accountRepository.save(newAccount);
        return incomeRepository.save(income);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        IncomeEntity income = incomeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Income not found"));
        if (!income.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized to delete this income");
        }

        AccountEntity account = income.getAccount();
        if (account != null) {
            balanceService.reverseIncome(account, income.getAmount());
            accountRepository.save(account);
        }
        incomeRepository.delete(income);
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

    private void populateEntityFromDTO(IncomeEntity entity, IncomeDTO dto, UUID userId) {
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setAmount(dto.getAmount());
        entity.setIncomeDate(dto.getDate());

        if (dto.getCategoryId() != null) {
            CategoryEntity category = categoryRepository.findById(dto.getCategoryId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
            if (!category.getUser().getId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid category");
            }
            if (!"INCOME".equals(category.getType())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category must be of type INCOME");
            }
            entity.setCategory(category);
        }

        if (dto.getAccountId() != null) {
            AccountEntity account = accountRepository.findById(dto.getAccountId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
            if (!account.getUser().getId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid account");
            }
            entity.setAccount(account);
        } else if (entity.getAccount() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account is mandatory for incomes");
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
