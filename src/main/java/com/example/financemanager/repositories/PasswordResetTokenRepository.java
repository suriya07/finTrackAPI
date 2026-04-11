package com.example.financemanager.repositories;

import com.example.financemanager.entities.PasswordResetTokenEntity;
import com.example.financemanager.entities.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, UUID> {
    Optional<PasswordResetTokenEntity> findByToken(String token);

    @Transactional
    void deleteByUser(UserEntity user);
}
