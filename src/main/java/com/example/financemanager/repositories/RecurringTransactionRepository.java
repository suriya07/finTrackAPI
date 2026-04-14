package com.example.financemanager.repositories;

import com.example.financemanager.entities.RecurringTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransactionEntity, UUID> {

    List<RecurringTransactionEntity> findByUser_IdOrderByNextDueDateAsc(UUID userId);

    @Query("SELECT r FROM RecurringTransactionEntity r WHERE r.user.id = :userId " +
           "AND r.isActive = true " +
           "AND r.nextDueDate <= :today " +
           "AND (r.endDate IS NULL OR r.endDate >= :today)")
    List<RecurringTransactionEntity> findDueTransactions(
            @Param("userId") UUID userId,
            @Param("today") LocalDate today);
}
