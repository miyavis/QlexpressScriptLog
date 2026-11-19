package com.example.rulelog.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(NotFoundException e) {
        // 业务异常消息是面向用户的（如"脚本不存在"），可安全回显
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(msg(e.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> badRequest(BadRequestException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(msg(e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> illegalArgument(IllegalArgumentException e) {
        // 参数校验类消息由平台生成，不含内部信息，回显给用户便于修正
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(msg(e.getMessage()));
    }

    /**
     * 兜底：服务端完整记录异常（含堆栈），客户端只回通用文案 + 追踪码，
     * 避免把类名/堆栈/SQL 等内部信息泄漏到公网 Demo。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unknown(Exception e) {
        String traceCode = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.error("未处理异常（traceCode={}）", traceCode, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(msg("服务器内部错误，请稍后重试（追踪码：" + traceCode + "）"));
    }

    private Map<String, Object> msg(String message) {
        return Collections.singletonMap("message", message);
    }
}
