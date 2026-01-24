package com.example.financemanager.repositories;

import com.example.financemanager.entities.BudgetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<BudgetEntity, UUID> {
    List<BudgetEntity> findByUserId(UUID userId);

    Optional<BudgetEntity> findByUserIdAndCategoryIdAndMonth(UUID userId, UUID categoryId, LocalDate month);

    boolean existsByUserIdAndCategoryIdAndMonth(UUID userId, UUID categoryId, LocalDate month);
}
