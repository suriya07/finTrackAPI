package com.example.financemanager.repositories;

import com.example.financemanager.entities.GroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GroupRepository extends JpaRepository<GroupEntity, UUID> {
    List<GroupEntity> findByUserId(UUID userId);

    boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);
}
