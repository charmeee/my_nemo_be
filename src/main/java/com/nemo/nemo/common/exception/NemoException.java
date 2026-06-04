package com.nemo.nemo.common.exception;

import lombok.Getter;

@Getter
public class NemoException extends RuntimeException {

    private final ErrorCode errorCode;

    public NemoException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public NemoException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }
}
