package com.example.rulelog.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

/** 认证状态探测：前端进入编辑态时调用，未认证返回 401（触发登录弹窗）。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** 当前登录用户信息（本接口需认证，由 SecurityConfig 保护） */
    @GetMapping("/status")
    public Map<String, Object> status(Principal principal) {
        Map<String, Object> info = new HashMap<>();
        info.put("authenticated", principal != null);
        info.put("username", principal == null ? null : principal.getName());
        return info;
    }
}
