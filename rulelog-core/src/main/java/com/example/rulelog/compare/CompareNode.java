package com.example.rulelog.compare;

import java.util.ArrayList;
import java.util.List;

/**
 * 比对节点：把多个数据源按 key 对齐，逐字段比对。
 *
 * - keyField: 关联字段（如"订单号"），各数据源按此字段的值对齐成一行
 * - sourceKeys: 参与比对的数据源 resultKey 列表（至少 2 个）
 * - fields: 要比对的字段定义
 */
public class CompareNode {

    private String id;
    private String name = "比对";
    private String keyField;              // 关联 key 字段名
    private List<String> sourceKeys = new ArrayList<>();  // 参与的数据源
    private List<FieldCompare> fields = new ArrayList<>();

    public CompareNode() {
    }

    // ---------- getters / setters ----------
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getKeyField() { return keyField; }
    public void setKeyField(String keyField) { this.keyField = keyField; }
    public List<String> getSourceKeys() { return sourceKeys; }
    public void setSourceKeys(List<String> sourceKeys) { this.sourceKeys = sourceKeys == null ? new ArrayList<>() : sourceKeys; }
    public List<FieldCompare> getFields() { return fields; }
    public void setFields(List<FieldCompare> fields) { this.fields = fields == null ? new ArrayList<>() : fields; }
}
