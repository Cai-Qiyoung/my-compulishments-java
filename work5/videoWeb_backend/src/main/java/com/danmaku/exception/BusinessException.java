package com.danmaku.exception;

public class BusinessException extends RuntimeException {
    Integer code;

    public BusinessException(String msg) {
        super(msg);
        this.code = -1;
    }

    public BusinessException(Integer code, String msg) {
        super(msg);
        this.code = code;
    }
}