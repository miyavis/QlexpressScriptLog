package com.example.rulelog.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 单行脚本的执行记录。
 * 用于前端“脚本每一行对应的日志”展示：业务人员可以清晰看到
 * 哪一行代码调用了哪个服务、产生了什么内部步骤、QLExpress表达式如何执行、结果如何。
 */
public class LineExecution {

    /** 脚本行号（从 1 开始） */
    private int lineNo;

    /** 该行脚本源码（截断保存） */
    private String lineContent;

    /** 该行是否产生了服务调用、日志或表达式执行 */
    private boolean hasActivity;

    /** 该行执行的返回结果（如该行是 返回 xxx） */
    private Object result;

    private String resultJson;

    /** 该行涉及的 Service 调用（扁平，不含嵌套 children） */
    private List<ServiceCallLog> calls = new ArrayList<>();

    /** 该行产生的根层级步骤（记录日志函数） */
    private List<StepLog> steps = new ArrayList<>();

    /** 该行产生的 QLExpress 表达式执行记录（如 IF 条件判断、变量赋值、RETURN 返回等） */
    private List<LineExpression> expressions = new ArrayList<>();

    public LineExecution() {
    }

    public LineExecution(int lineNo, String lineContent) {
        this.lineNo = lineNo;
        this.lineContent = lineContent;
    }

    public int getLineNo() {
        return lineNo;
    }

    public void setLineNo(int lineNo) {
        this.lineNo = lineNo;
    }

    public String getLineContent() {
        return lineContent;
    }

    public void setLineContent(String lineContent) {
        this.lineContent = lineContent;
    }

    public boolean isHasActivity() {
        return hasActivity;
    }

    public void setHasActivity(boolean hasActivity) {
        this.hasActivity = hasActivity;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
        this.resultJson = com.example.rulelog.util.JsonUtils.toJsonTruncated(result, 1000);
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public List<ServiceCallLog> getCalls() {
        return calls;
    }

    public void setCalls(List<ServiceCallLog> calls) {
        this.calls = calls;
    }

    public List<StepLog> getSteps() {
        return steps;
    }

    public void setSteps(List<StepLog> steps) {
        this.steps = steps;
    }

    public List<LineExpression> getExpressions() {
        return expressions;
    }

    public void setExpressions(List<LineExpression> expressions) {
        this.expressions = expressions;
    }

    public void addCall(ServiceCallLog call) {
        this.calls.add(call);
        this.hasActivity = true;
    }

    public void addStep(StepLog step) {
        this.steps.add(step);
        this.hasActivity = true;
    }

    public void addExpression(LineExpression expression) {
        this.expressions.add(expression);
        this.hasActivity = true;
    }

    /** QLExpress 单个表达式执行记录 */
    public static class LineExpression {

        /** 表达式类型：IF / RETURN / VARIABLE / OPERATOR / VALUE / FUNCTION / METHOD 等 */
        private String type;

        /** 表达式 token，如 "积分", "100", "打折积分", "如果", "则", "否则", "返回" */
        private String token;

        /** 表达式计算结果，如 true, 200, 0.88 */
        private Object value;

        /** 是否被求值（短路则为 false） */
        private boolean evaluated;

        public LineExpression() {
        }

        public LineExpression(String type, String token, Object value, boolean evaluated) {
            this.type = type;
            this.token = token;
            this.value = value;
            this.evaluated = evaluated;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
            this.valueJson = com.example.rulelog.util.JsonUtils.toJsonTruncated(value, 500);
        }

        public String getValueJson() {
            return valueJson;
        }

        public void setValueJson(String valueJson) {
            this.valueJson = valueJson;
        }

        public boolean isEvaluated() {
            return evaluated;
        }

        public void setEvaluated(boolean evaluated) {
            this.evaluated = evaluated;
        }

        private String valueJson;

        /** 友好显示名称，如 "如果 积分 大于 100" */
        public String getDisplayName() {
            String typeLabel = typeLabel(type);
            StringBuilder sb = new StringBuilder();
            if (typeLabel != null) {
                sb.append(typeLabel).append(" ");
            }
            if (token != null && !token.isEmpty()) {
                sb.append(token);
            }
            if (sb.length() == 0) {
                sb.append(type);
            }
            return sb.toString();
        }

        private String typeLabel(String type) {
            if (type == null) return null;
            switch (type) {
                case "IF": return "如果";
                case "RETURN": return "返回";
                case "VARIABLE": return "赋值";
                case "OPERATOR": return "运算";
                case "VALUE": return "值";
                case "FUNCTION": return "函数";
                case "METHOD": return "方法";
                case "FIELD": return "字段";
                case "LIST": return "列表";
                case "MAP": return "映射";
                case "SWITCH": return "开关";
                case "BLOCK": return "块";
                case "PRIMARY": return "主表达式";
                case "STATEMENT": return "语句";
                case "DEFINE_FUNCTION": return "定义函数";
                case "DEFINE_MACRO": return "定义宏";
                default: return type;
            }
        }
    }
}

