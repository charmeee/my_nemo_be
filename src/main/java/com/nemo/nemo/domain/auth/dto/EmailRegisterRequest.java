package com.nemo.nemo.domain.auth.dto;

public record EmailRegisterRequest(String email, String password, String nickname) {}
