package com.example.rulelog.param;

import java.util.ArrayList;
import java.util.List;

/**
 * 参数定义（递归模型）：描述规则/函数的一个参数的元信息。
 *
 * 支持类型：string / number / boolean / object / array
 * - object：fields 描述子字段（可继续嵌套 object/array）
 * - array：element 描述元素类型（元素本身可又是 object）
 *
 * 该模型贯穿：注解 → SDK → HTTP 契约 → 前端渲染，是参数体系的唯一事实源。
 */
public class ParamDef {

    private String name;          // 参数名（脚本里用的标识）
    private String label;         // 中文名（界面展示）
    private String type = "string"; // string/number/boolean/object/array
    private String desc = "";     // 含义说明
    private boolean required = false;
    private List<ParamDef> fields = new ArrayList<>(); // object 的子字段
    private ParamDef element;     // array 的元素定义

    public ParamDef() {
    }

    public ParamDef(String name, String label, String type) {
        this.name = name;
        this.label = label;
        this.type = type;
    }

    // ---------- getters / setters ----------
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public List<ParamDef> getFields() { return fields; }
    public void setFields(List<ParamDef> fields) { this.fields = fields == null ? new ArrayList<>() : fields; }

    public ParamDef getElement() { return element; }
    public void setElement(ParamDef element) { this.element = element; }

    /** 是否容器类型（object/array） */
    public boolean isComplex() {
        return "object".equals(type) || "array".equals(type);
    }
}
