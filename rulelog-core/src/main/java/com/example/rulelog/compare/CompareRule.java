package com.example.rulelog.compare;

import java.util.ArrayList;
import java.util.List;

/**
 * 比对规则定义：一组取数节点 + 一个比对节点。
 * 业务人员在比对画布上配置，保存到 QL_COMPARE_RULE 表。
 */
public class CompareRule {

    private String id;
    private String name;
    private String description;
    private List<FetchNode> fetchNodes = new ArrayList<>();
    private CompareNode compareNode = new CompareNode();
    private long createdAt;
    private long updatedAt;

    public CompareRule() {
    }

    // ---------- getters / setters ----------
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<FetchNode> getFetchNodes() { return fetchNodes; }
    public void setFetchNodes(List<FetchNode> fetchNodes) { this.fetchNodes = fetchNodes == null ? new ArrayList<>() : fetchNodes; }
    public CompareNode getCompareNode() { return compareNode; }
    public void setCompareNode(CompareNode compareNode) { this.compareNode = compareNode == null ? new CompareNode() : compareNode; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
