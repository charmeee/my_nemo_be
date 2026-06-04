package com.nemo.nemo.domain.invite.repository;

import com.nemo.nemo.domain.invite.entity.InviteLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InviteLinkRepository extends JpaRepository<InviteLink, UUID> {

    Optional<InviteLink> findByCode(String code);

    List<InviteLink> findByAlbumId(UUID albumId);
}
