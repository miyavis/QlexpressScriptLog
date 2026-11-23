package com.example.rulelog.controller;

import com.example.rulelog.express.FunctionAliasRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 函数注册表接口：把平台已注册的中文函数（含所属 Service、方法名、参数提示）下发给前端。
 * 前端卡片画布据此生成"取数卡"的函数下拉与参数占位符——新增业务 Service 只要注册一次，
 * 画布自动多一个可选函数，无需改前端代码（产品化：注册表驱动，不再写死 3 个函数）。
 */
@RestController
@RequestMapping("/api/functions")
public class FunctionRegistryController {

    private final FunctionAliasRegistry aliasRegistry;

    public FunctionRegistryController(FunctionAliasRegistry aliasRegistry) {
        this.aliasRegistry = aliasRegistry;
    }

    /** 已注册的全部中文函数（按注册顺序）：chineseName / serviceName / methodName / paramHint */
    @GetMapping
    public List<Map<String, Object>> list() {
        return aliasRegistry.list();
    }
}
