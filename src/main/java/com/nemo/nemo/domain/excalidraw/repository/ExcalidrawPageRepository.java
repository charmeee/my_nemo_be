package com.nemo.nemo.domain.excalidraw.repository;

import com.nemo.nemo.domain.excalidraw.entity.ExcalidrawPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExcalidrawPageRepository extends JpaRepository<ExcalidrawPage, UUID> {

    @Query("SELECT p FROM ExcalidrawPage p WHERE p.album.id = :albumId AND p.deletedAt IS NULL ORDER BY p.pageOrder ASC")
    List<ExcalidrawPage> findByAlbumIdOrderByPageOrder(@Param("albumId") UUID albumId);

    @Query("SELECT p FROM ExcalidrawPage p WHERE p.pageId = :pageId AND p.album.id = :albumId AND p.deletedAt IS NULL")
    Optional<ExcalidrawPage> findByPageIdAndAlbumId(@Param("pageId") UUID pageId, @Param("albumId") UUID albumId);

    long countByAlbumIdAndDeletedAtIsNull(UUID albumId);
}
