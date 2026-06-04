package com.nemo.nemo.domain.member.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "member_oauth")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberOAuth {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Enumerated(EnumType.STRING)
    private AuthProvider provider;

    private String providerId;

    public static MemberOAuth create(Member member, AuthProvider provider, String providerId) {
        MemberOAuth oauth = new MemberOAuth();
        oauth.member = member;
        oauth.provider = provider;
        oauth.providerId = providerId;
        return oauth;
    }
}
