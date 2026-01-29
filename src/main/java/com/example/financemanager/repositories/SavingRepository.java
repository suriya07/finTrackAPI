package com.example.financemanager.repositories;

import com.example.financemanager.entities.SavingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface SavingRepository extends JpaRepository<SavingEntity, UUID> {
    List<SavingEntity> findByUserId(UUID userId);

    List<SavingEntity> findByUserIdAndCreatedAtBeforeOrderByCreatedAtDesc(UUID userId, Instant date);
}
