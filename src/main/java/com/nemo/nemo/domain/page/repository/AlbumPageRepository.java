package com.nemo.nemo.domain.page.repository;

import com.nemo.nemo.domain.page.entity.AlbumPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlbumPageRepository extends JpaRepository<AlbumPage, UUID> {

    List<AlbumPage> findByAlbumIdAndDeletedAtIsNull(UUID albumId);

    Optional<AlbumPage> findByTlPageIdAndAlbumIdAndDeletedAtIsNull(UUID tlPageId, UUID albumId);

    @Modifying
    @Query("UPDATE AlbumPage p SET p.deletedAt = null WHERE p.tlPageId = :tlPageId")
    void restorePage(@Param("tlPageId") UUID tlPageId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM AlbumPage p WHERE p.album.id = :albumId")
    void deleteAllByAlbumId(@Param("albumId") UUID albumId);
}
