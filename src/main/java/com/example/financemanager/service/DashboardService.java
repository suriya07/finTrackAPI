package com.example.financemanager.service;

import com.example.financemanager.dto.BudgetStatusDTO;
import com.example.financemanager.dto.CategorySpendDTO;
import com.example.financemanager.dto.MonthlySummaryDTO;
import com.example.financemanager.dto.TrendPointDTO;
import com.example.financemanager.entities.BudgetEntity;
import com.example.financemanager.entities.CategoryEntity;
import com.example.financemanager.entities.ExpenseEntity;
import com.example.financemanager.entities.IncomeEntity;
import com.example.financemanager.repositories.BudgetRepository;
import com.example.financemanager.repositories.ExpenseRepository;
import com.example.financemanager.repositories.IncomeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only aggregations powering the dashboard: monthly summary, category
 * breakdowns, multi-month trends, and budget-vs-actual tracking.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final BudgetRepository budgetRepository;
    private final CategoryHierarchyService categoryHierarchy;

    public DashboardService(ExpenseRepository expenseRepository,
            IncomeRepository incomeRepository,
            BudgetRepository budgetRepository,
            CategoryHierarchyService categoryHierarchy) {
        this.expenseRepository = expenseRepository;
        this.incomeRepository = incomeRepository;
        this.budgetRepository = budgetRepository;
        this.categoryHierarchy = categoryHierarchy;
    }

    public MonthlySummaryDTO summary(UUID userId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        BigDecimal totalExpense = sumExpenses(userId, start, end);
        BigDecimal totalIncome = sumIncomes(userId, start, end);
        BigDecimal totalBudget = budgetRepository.findByUserId(userId).stream()
                .filter(b -> b.getMonth() != null && b.getMonth().getMonthValue() == month
                        && b.getMonth().getYear() == year)
                .map(BudgetEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new MonthlySummaryDTO(
                year, month,
                totalIncome,
                totalExpense,
                totalIncome.subtract(totalExpense),
                totalBudget,
                totalBudget.subtract(totalExpense));
    }

    public List<CategorySpendDTO> categoryBreakdown(UUID userId, int year, int month, String type) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        Map<UUID, CategorySpendDTO> byCategory = new LinkedHashMap<>();
        if ("INCOME".equalsIgnoreCase(type)) {
            for (IncomeEntity i : incomeRepository.findByUserIdAndIncomeDateBetweenOrderByIncomeDateDesc(userId, start, end)) {
                accumulate(byCategory, i.getCategory(), i.getAmount());
            }
        } else {
            for (ExpenseEntity e : expenseRepository.findByUserIdAndExpenseDateBetweenOrderByExpenseDateDesc(userId, start, end)) {
                accumulate(byCategory, e.getCategory(), e.getAmount());
            }
        }

        List<CategorySpendDTO> result = new ArrayList<>(byCategory.values());
        result.sort(Comparator.comparing(CategorySpendDTO::total).reversed());
        return result;
    }

    public List<TrendPointDTO> trends(UUID userId, int months) {
        int window = Math.max(1, Math.min(months, 36));
        List<TrendPointDTO> points = new ArrayList<>();
        YearMonth current = YearMonth.now();

        for (int i = window - 1; i >= 0; i--) {
            YearMonth ym = current.minusMonths(i);
            LocalDate start = ym.atDay(1);
            LocalDate end = ym.atEndOfMonth();
            BigDecimal income = sumIncomes(userId, start, end);
            BigDecimal expense = sumExpenses(userId, start, end);
            points.add(new TrendPointDTO(ym.getYear(), ym.getMonthValue(), income, expense, income.subtract(expense)));
        }
        return points;
    }

    public List<BudgetStatusDTO> budgetStatus(UUID userId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        List<ExpenseEntity> monthExpenses =
                expenseRepository.findByUserIdAndExpenseDateBetweenOrderByExpenseDateDesc(userId, start, end);

        List<BudgetStatusDTO> result = new ArrayList<>();
        for (BudgetEntity budget : budgetRepository.findByUserId(userId)) {
            if (budget.getMonth() == null
                    || budget.getMonth().getMonthValue() != month
                    || budget.getMonth().getYear() != year) {
                continue;
            }

            CategoryEntity category = budget.getCategory();
            Set<UUID> categoryIds = categoryHierarchy.selfAndDescendantIds(category.getId());

            BigDecimal spent = monthExpenses.stream()
                    .filter(e -> e.getCategory() != null && categoryIds.contains(e.getCategory().getId()))
                    .map(ExpenseEntity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal budgeted = budget.getAmount();
            BigDecimal remaining = budgeted.subtract(spent);
            double percentUsed = budgeted.compareTo(BigDecimal.ZERO) > 0
                    ? spent.divide(budgeted, 4, RoundingMode.HALF_UP).doubleValue() * 100.0
                    : 0.0;

            result.add(new BudgetStatusDTO(
                    budget.getId(),
                    category.getId(),
                    category.getName(),
                    budgeted,
                    spent,
                    remaining,
                    Math.round(percentUsed * 100.0) / 100.0,
                    spent.compareTo(budgeted) > 0));
        }
        return result;
    }

    private void accumulate(Map<UUID, CategorySpendDTO> map, CategoryEntity category, BigDecimal amount) {
        UUID key = category != null ? category.getId() : null;
        String name = category != null ? category.getName() : "Uncategorized";
        CategorySpendDTO existing = map.get(key);
        BigDecimal newTotal = (existing != null ? existing.total() : BigDecimal.ZERO).add(amount);
        map.put(key, new CategorySpendDTO(key, name, newTotal));
    }

    private BigDecimal sumExpenses(UUID userId, LocalDate start, LocalDate end) {
        return expenseRepository.findByUserIdAndExpenseDateBetweenOrderByExpenseDateDesc(userId, start, end).stream()
                .map(ExpenseEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumIncomes(UUID userId, LocalDate start, LocalDate end) {
        return incomeRepository.findByUserIdAndIncomeDateBetweenOrderByIncomeDateDesc(userId, start, end).stream()
                .map(i -> i.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
