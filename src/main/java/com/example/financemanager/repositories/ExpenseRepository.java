package com.example.financemanager.repositories;

import com.example.financemanager.entities.ExpenseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ExpenseRepository
                extends JpaRepository<ExpenseEntity, UUID> {

        List<ExpenseEntity> findByUserId(UUID userId);

        List<ExpenseEntity> findByUserIdAndExpenseDateBetweenOrderByExpenseDateDesc(UUID userId, LocalDate startDate,
                        LocalDate endDate);

        List<ExpenseEntity> findByDebt_Id(UUID debtId);

        List<ExpenseEntity> findBySaving_Id(UUID savingId);

        boolean existsByCategoryId(UUID categoryId);
}
