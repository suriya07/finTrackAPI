package com.example.financemanager.repositories;

import com.example.financemanager.entities.IncomeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface IncomeRepository extends JpaRepository<IncomeEntity, UUID> {
    List<IncomeEntity> findByUserIdOrderByIncomeDateDesc(UUID userId);

    List<IncomeEntity> findByUserIdAndIncomeDateBetweenOrderByIncomeDateDesc(UUID userId, LocalDate startDate,
            LocalDate endDate);
}
