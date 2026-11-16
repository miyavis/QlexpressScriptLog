package com.example.rulelog.exception;

/** 请求参数非法（由 GlobalExceptionHandler 转成 400 JSON） */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
