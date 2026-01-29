package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowNodeDefinition;
import com.digital.video.gateway.workflow.FlowNodeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 条件分支节点：根据 field/operator/value 求值，将结果写入 _last_node_branch（true/false），
 * 下游连接用 condition="true" 或 "false" 选择分支。
 */
public class ConditionHandler implements FlowNodeHandler {
    private static final Logger logger = LoggerFactory.getLogger(ConditionHandler.class);

    @Override
    public boolean execute(FlowNodeDefinition node, FlowContext context) throws Exception {
        Map<String, Object> cfg = node.getConfig();
        if (cfg == null) {
            context.putVariable("_last_node_branch", "false");
            return true;
        }
        String field = cfg.get("field") != null ? cfg.get("field").toString().trim() : null;
        String operator = cfg.get("operator") != null ? cfg.get("operator").toString().trim().toLowerCase() : "eq";
        Object value = cfg.get("value");

        if (field == null || field.isEmpty()) {
            logger.warn("condition 节点未配置 field，走 false 分支");
            context.putVariable("_last_node_branch", "false");
            return true;
        }

        Object actual = getFieldFromContext(context, field);
        boolean result = evaluate(operator, actual, value);
        context.putVariable("_last_node_branch", result ? "true" : "false");
        logger.debug("condition 节点: field={}, operator={}, value={}, actual={}, result={}",
                field, operator, value, actual, result);
        return true;
    }

    private static Object getFieldFromContext(FlowContext context, String field) {
        if (context.getPayload() != null && context.getPayload().containsKey(field)) {
            return context.getPayload().get(field);
        }
        if (context.getVariables() != null && context.getVariables().containsKey(field)) {
            return context.getVariables().get(field);
        }
        if ("event_key".equals(field) && context.getAlarmType() != null) {
            return context.getAlarmType();
        }
        if ("assemblyId".equals(field)) {
            return context.getAssemblyId();
        }
        if ("deviceId".equals(field)) {
            return context.getDeviceId();
        }
        return null;
    }

    private static boolean evaluate(String operator, Object actual, Object expected) {
        String actualStr = actual != null ? actual.toString().trim() : "";
        switch (operator) {
            case "eq":
                return Objects.equals(actualStr, expected != null ? expected.toString().trim() : "");
            case "ne":
                return !Objects.equals(actualStr, expected != null ? expected.toString().trim() : "");
            case "in":
                if (expected instanceof List) {
                    List<String> list = ((List<?>) expected).stream()
                            .map(o -> o != null ? o.toString().trim() : "")
                            .collect(Collectors.toList());
                    return list.contains(actualStr);
                }
                return false;
            case "not_in":
                if (expected instanceof List) {
                    List<String> list = ((List<?>) expected).stream()
                            .map(o -> o != null ? o.toString().trim() : "")
                            .collect(Collectors.toList());
                    return !list.contains(actualStr);
                }
                return true;
            default:
                return Objects.equals(actualStr, expected != null ? expected.toString().trim() : "");
        }
    }
}
