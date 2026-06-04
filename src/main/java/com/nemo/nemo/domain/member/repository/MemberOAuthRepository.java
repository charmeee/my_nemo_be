package com.nemo.nemo.domain.member.repository;

import com.nemo.nemo.domain.member.entity.AuthProvider;
import com.nemo.nemo.domain.member.entity.MemberOAuth;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MemberOAuthRepository extends JpaRepository<MemberOAuth, UUID> {
    Optional<MemberOAuth> findByProviderAndProviderId(AuthProvider provider, String providerId);
}
