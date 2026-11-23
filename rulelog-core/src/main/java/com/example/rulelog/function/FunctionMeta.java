package com.example.rulelog.function;

import com.example.rulelog.param.ParamDef;

import java.util.ArrayList;
import java.util.List;

/**
 * 中文函数元信息：函数名 + 说明 + 归属 + 参数定义。
 * 由注解扫描生成，也用于 /api/functions 下发（取代旧的 paramHint 单字符串）。
 */
public class FunctionMeta {

    private String name;          // 中文函数名
    private String desc = "";     // 函数说明
    private String serviceName;   // 所属 Service（平台侧）
    private String methodName;    // Java 方法名
    private List<ParamDef> params = new ArrayList<>();

    public FunctionMeta() {
    }

    public FunctionMeta(String name, String desc) {
        this.name = name;
        this.desc = desc;
    }

    // ---------- getters / setters ----------
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }

    public List<ParamDef> getParams() { return params; }
    public void setParams(List<ParamDef> params) { this.params = params == null ? new ArrayList<>() : params; }
}
