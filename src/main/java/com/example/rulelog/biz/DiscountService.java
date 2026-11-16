package com.example.rulelog.biz;

import com.example.rulelog.trace.StepLogger;
import org.springframework.stereotype.Service;

/**
 * 模拟业务 Service 二：折扣域。
 * 内部嵌套调用 UserService，演示日志里可展开的父子调用链。
 */
@Service
public class DiscountService {

    private final UserService userService;

    public DiscountService(UserService userService) {
        this.userService = userService;
    }

    /** 计算折扣率，例如 0.88 表示 88 折 */
    public double calcDiscount(String user) {
        StepLogger.log("读取营销活动配置", "活动=周年庆, 门槛=会员等级联动");

        // 嵌套调用：AOP 会在外层 DiscountService.calcDiscount 下挂出 UserService 调用
        int points = userService.queryPoints(user);
        StepLogger.log("按积分微调折扣", "可用积分=" + points);

        boolean isVip = "张三".equals(user) || "王五".equals(user);
        double discount = isVip ? 0.88 : 0.95;
        StepLogger.log("得出最终折扣", "用户=" + user + ", 折扣=" + discount);
        return discount;
    }
}
