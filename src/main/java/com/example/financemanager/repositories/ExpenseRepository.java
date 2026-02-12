package com.example.financemanager.repositories;

import com.example.financemanager.entities.ExpenseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ExpenseRepository
                extends JpaRepository<ExpenseEntity, UUID> {

        List<ExpenseEntity> findByUserId(UUID userId);

        List<ExpenseEntity> findByUserIdAndExpenseDateGreaterThanEqualOrderByExpenseDateDesc(UUID userId,
                        LocalDate startDate);

        List<ExpenseEntity> findByUserIdAndExpenseDateLessThanEqualOrderByExpenseDateDesc(UUID userId,
                        LocalDate endDate);

        @Query(value = "SELECT e.* FROM expenses e " +
                        "LEFT JOIN categories c ON c.id = e.category_id " +
                        "LEFT JOIN accounts a ON a.id = e.account_id " +
                        "WHERE e.user_id = :userId " +
                        "AND (CAST(:startDate AS date) IS NULL OR e.expense_date >= CAST(:startDate AS date)) " +
                        "AND (CAST(:endDate AS date) IS NULL OR e.expense_date <= CAST(:endDate AS date)) " +
                        "AND (CAST(:accountId AS uuid) IS NULL OR a.id = CAST(:accountId AS uuid)) " +
                        "AND (:categoryIds IS NULL OR c.id IN :categoryIds) " +
                        "AND (:search IS NULL OR " +
                        "     (e.name::text ILIKE CONCAT('%', :search, '%')) OR " +
                        "     (e.description IS NOT NULL AND e.description::text ILIKE CONCAT('%', :search, '%'))) " +
                        "ORDER BY e.expense_date DESC", nativeQuery = true)
        List<ExpenseEntity> findFilteredExpenses(
                        @Param("userId") UUID userId,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("categoryIds") java.util.Collection<UUID> categoryIds,
                        @Param("accountId") UUID accountId,
                        @Param("search") String search);

        List<ExpenseEntity> findByUserIdAndExpenseDateBetweenOrderByExpenseDateDesc(UUID userId, LocalDate startDate,
                        LocalDate endDate);

        List<ExpenseEntity> findByDebt_Id(UUID debtId);

        List<ExpenseEntity> findBySaving_Id(UUID savingId);

        List<ExpenseEntity> findByAccount_Id(UUID accountId);

        List<ExpenseEntity> findByAccount_IdAndExpenseDateGreaterThanEqual(UUID accountId, LocalDate date);

        boolean existsByCategoryId(UUID categoryId);
}
