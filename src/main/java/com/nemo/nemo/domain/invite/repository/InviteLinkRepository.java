package com.nemo.nemo.domain.invite.repository;

import com.nemo.nemo.domain.invite.entity.InviteLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InviteLinkRepository extends JpaRepository<InviteLink, UUID> {

    Optional<InviteLink> findByCode(String code);

    List<InviteLink> findByAlbumId(UUID albumId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM InviteLink il WHERE il.album.id = :albumId")
    void deleteAllByAlbumId(@Param("albumId") UUID albumId);
}
