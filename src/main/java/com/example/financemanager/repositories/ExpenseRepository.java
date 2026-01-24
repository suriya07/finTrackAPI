package com.example.financemanager.repositories;

import com.example.financemanager.entities.ExpenseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExpenseRepository
        extends JpaRepository<ExpenseEntity, UUID> {

    List<ExpenseEntity> findByUserId(UUID userId);

    List<ExpenseEntity> findByDebt_Id(UUID debtId);

    List<ExpenseEntity> findBySaving_Id(UUID savingId);
}
