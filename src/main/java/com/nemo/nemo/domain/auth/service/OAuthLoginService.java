package com.nemo.nemo.domain.auth.service;

import com.nemo.nemo.domain.auth.dto.OAuthUserInfo;
import com.nemo.nemo.domain.member.entity.Member;
import com.nemo.nemo.domain.member.entity.MemberOAuth;
import com.nemo.nemo.domain.member.repository.MemberOAuthRepository;
import com.nemo.nemo.domain.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuthLoginService {

    private final MemberRepository memberRepository;
    private final MemberOAuthRepository memberOAuthRepository;

    public OAuthLoginService(MemberRepository memberRepository, MemberOAuthRepository memberOAuthRepository) {
        this.memberRepository = memberRepository;
        this.memberOAuthRepository = memberOAuthRepository;
    }

    // provider+providerId로 OAuth 매핑 조회, 없으면 이메일로 회원 찾거나 신규 생성 후 매핑 연결
    @Transactional
    public Member findOrCreateMember(OAuthUserInfo info) {
        return memberOAuthRepository
                .findByProviderAndProviderId(info.provider(), info.providerId())
                .map(MemberOAuth::getMember)
                .orElseGet(() -> {
                    Member member = memberRepository.findByEmail(info.email())
                            .orElseGet(() -> memberRepository.save(
                                    Member.create(info.email(), info.nickname(), info.profileImage())
                            ));
                    memberOAuthRepository.save(MemberOAuth.create(member, info.provider(), info.providerId()));
                    return member;
                });
    }
}
