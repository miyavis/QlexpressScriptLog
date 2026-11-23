package com.example.rulelog.biz;

import com.example.rulelog.trace.StepLogger;
import org.springframework.stereotype.Service;

/**
 * 模拟业务 Service 一：用户域。
 * 关键业务步骤通过 StepLogger.log 手动打点，
 * 与 AOP 记录的入口/出口一起，构成完整的服务内部过程。
 */
@Service
public class UserService {

    /** 查询用户会员等级：VIP 用户白名单模拟为内存映射 */
    public String queryLevel(String user) {
        StepLogger.log("查询会员基础信息", "用户=" + user + ", 读取注册资料");
        sleepQuietly(20);

        StepLogger.log("计算会员等级", "按等级表归档");
        // 模拟业务规则：张三 / 王五 是 VIP
        boolean vip = "张三".equals(user) || "王五".equals(user);
        return vip ? "VIP" : "普通";
    }

    /** 查询用户积分 */
    public int queryPoints(String user) {
        StepLogger.log("读取积分流水", "用户=" + user + ", 汇总最近30天");
        sleepQuietly(15);

        StepLogger.log("合成可用积分", "扣除已过期部分");
        return "张三".equals(user) ? 380 : 60;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
