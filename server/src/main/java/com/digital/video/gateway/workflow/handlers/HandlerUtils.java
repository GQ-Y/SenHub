package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.workflow.FlowContext;

import java.util.Map;

/**
 * 节点处理器常用工具
 */
public class HandlerUtils {
    private HandlerUtils() {}

    public static String renderTemplate(String template, FlowContext context, Map<String, Object> extra) {
        if (template == null) {
            return null;
        }
        String result = template;
        if (context != null) {
            if (context.getDeviceId() != null) {
                result = result.replace("{deviceId}", context.getDeviceId());
            }
            if (context.getAssemblyId() != null) {
                result = result.replace("{assemblyId}", context.getAssemblyId());
            }
            if (context.getAlarmType() != null) {
                result = result.replace("{alarmType}", context.getAlarmType());
            }
            if (context.getFlowId() != null) {
                result = result.replace("{flowId}", context.getFlowId());
            }
        }
        if (extra != null) {
            for (Map.Entry<String, Object> entry : extra.entrySet()) {
                if (entry.getValue() != null) {
                    result = result.replace("{" + entry.getKey() + "}", entry.getValue().toString());
                }
            }
        }
        return result;
    }
}
