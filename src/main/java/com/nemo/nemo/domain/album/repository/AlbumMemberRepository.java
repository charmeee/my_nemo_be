package com.nemo.nemo.domain.album.repository;

import com.nemo.nemo.domain.album.entity.AlbumMember;
import com.nemo.nemo.domain.album.entity.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlbumMemberRepository extends JpaRepository<AlbumMember, UUID> {

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM AlbumMember am WHERE am.album.id = :albumId")
    void deleteAllByAlbumId(@Param("albumId") UUID albumId);

    @Query("SELECT am FROM AlbumMember am WHERE am.album.id = :albumId AND am.user.id = :userId")
    Optional<AlbumMember> findByAlbumIdAndUserId(@Param("albumId") UUID albumId, @Param("userId") UUID userId);

    @Query("SELECT am FROM AlbumMember am WHERE am.album.id = :albumId AND am.user.id = :userId AND am.status = com.nemo.nemo.domain.album.entity.MemberStatus.ACTIVE")
    Optional<AlbumMember> findActiveByAlbumIdAndUserId(@Param("albumId") UUID albumId, @Param("userId") UUID userId);

    List<AlbumMember> findByAlbumIdAndStatus(UUID albumId, MemberStatus status);

    @Query("SELECT am FROM AlbumMember am WHERE am.album.id = :albumId AND am.status = :status ORDER BY am.joinedAt ASC")
    List<AlbumMember> findByAlbumIdAndStatusOrderByJoinedAt(@Param("albumId") UUID albumId, @Param("status") MemberStatus status);

    long countByAlbumIdAndStatus(UUID albumId, MemberStatus status);
}
