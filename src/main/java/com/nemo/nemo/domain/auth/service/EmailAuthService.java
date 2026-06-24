package com.nemo.nemo.domain.auth.service;

import com.nemo.nemo.common.exception.ErrorCode;
import com.nemo.nemo.common.exception.NemoException;
import com.nemo.nemo.domain.member.entity.Member;
import com.nemo.nemo.domain.member.repository.MemberRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailAuthService {

    private final MemberRepository memberRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public EmailAuthService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    // 이메일 중복 검사 후 bcrypt 해시 저장하여 회원 생성
    @Transactional
    public Member register(String email, String password, String nickname) {
        if (memberRepository.findByEmail(email).isPresent()) {
            throw new NemoException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        String hash = passwordEncoder.encode(password);
        return memberRepository.save(Member.createWithPassword(email, hash, nickname));
    }

    // 이메일 조회 + bcrypt 비교로 로그인 검증
    @Transactional(readOnly = true)
    public Member login(String email, String password) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new NemoException(ErrorCode.INVALID_CREDENTIALS));
        if (member.getPasswordHash() == null || !passwordEncoder.matches(password, member.getPasswordHash())) {
            throw new NemoException(ErrorCode.INVALID_CREDENTIALS);
        }
        return member;
    }
}
