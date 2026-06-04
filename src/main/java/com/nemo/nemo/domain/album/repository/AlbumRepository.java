package com.nemo.nemo.domain.album.repository;

import com.nemo.nemo.domain.album.entity.Album;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AlbumRepository extends JpaRepository<Album, UUID> {

    List<Album> findByCreatorIdAndDeletedAtIsNull(UUID creatorId);

    @Query("SELECT a FROM Album a JOIN AlbumMember am ON am.album.id = a.id WHERE am.user.id = :userId AND am.status = 'ACTIVE' AND a.deletedAt IS NULL")
    List<Album> findAlbumsByMemberId(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE Album a SET a.deletedAt = null WHERE a.id = :albumId")
    void restoreAlbum(@Param("albumId") UUID albumId);
}
