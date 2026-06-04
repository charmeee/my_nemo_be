package com.nemo.nemo.domain.trash.repository;

import com.nemo.nemo.domain.trash.entity.Trash;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TrashRepository extends JpaRepository<Trash, UUID> {

    List<Trash> findByDeletedByIdOrderByCreatedAtDesc(UUID userId);

    @Modifying
    @Query("DELETE FROM Trash t WHERE t.expiresAt < :now")
    void deleteExpired(@Param("now") LocalDateTime now);
}
