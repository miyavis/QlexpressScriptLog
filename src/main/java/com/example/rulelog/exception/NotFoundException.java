package com.example.rulelog.exception;

/** 业务资源不存在（由 GlobalExceptionHandler 转成 404 JSON） */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
