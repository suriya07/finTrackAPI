package com.example.financemanager.repositories;

import com.example.financemanager.entities.IncomeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface IncomeRepository extends JpaRepository<IncomeEntity, UUID> {
        List<IncomeEntity> findByUserIdOrderByIncomeDateDesc(UUID userId);

        List<IncomeEntity> findByUserIdAndIncomeDateBetweenOrderByIncomeDateDesc(UUID userId, LocalDate startDate,
                        LocalDate endDate);

        @Query(value = "SELECT i.* FROM incomes i " +
                        "LEFT JOIN categories c ON c.id = i.category_id " +
                        "WHERE i.user_id = :userId " +
                        "AND (CAST(:startDate AS date) IS NULL OR i.income_date >= CAST(:startDate AS date)) " +
                        "AND (CAST(:endDate AS date) IS NULL OR i.income_date <= CAST(:endDate AS date)) " +
                        "AND (CAST(:accountId AS uuid) IS NULL OR i.account_id = CAST(:accountId AS uuid)) " +
                        "AND (:categoryIds IS NULL OR c.id IN :categoryIds) " +
                        "AND (:search IS NULL OR " +
                        "     (i.name::text ILIKE CONCAT('%', :search, '%')) OR " +
                        "     (i.description IS NOT NULL AND i.description::text ILIKE CONCAT('%', :search, '%'))) " +
                        "ORDER BY i.income_date DESC", nativeQuery = true)
        List<IncomeEntity> findFilteredIncomes(
                        @Param("userId") UUID userId,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("accountId") UUID accountId,
                        @Param("categoryIds") java.util.Collection<UUID> categoryIds,
                        @Param("search") String search);

        List<IncomeEntity> findByAccount_Id(UUID accountId);

        boolean existsByCategoryId(UUID categoryId);
}
