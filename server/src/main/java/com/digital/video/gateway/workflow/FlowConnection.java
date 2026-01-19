package com.digital.video.gateway.workflow;

/**
 * 节点连接定义
 */
public class FlowConnection {
    private String from;
    private String to;
    /**
     * 条件：success / failure / null(无条件)
     */
    private String condition;

    public FlowConnection() {
    }

    public FlowConnection(String from, String to, String condition) {
        this.from = from;
        this.to = to;
        this.condition = condition;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }
}
