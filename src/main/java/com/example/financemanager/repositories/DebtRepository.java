package com.example.financemanager.repositories;

import com.example.financemanager.entities.DebtEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface DebtRepository extends JpaRepository<DebtEntity, UUID> {
    List<DebtEntity> findByUserId(UUID userId);

    List<DebtEntity> findByUserIdAndCreatedAtBeforeOrderByCreatedAtDesc(UUID userId, Instant date);
}
