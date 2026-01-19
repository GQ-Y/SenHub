package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.service.OssService;
import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowNodeDefinition;
import com.digital.video.gateway.workflow.FlowNodeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * OSS上传节点
 */
public class OssUploadHandler implements FlowNodeHandler {
    private static final Logger logger = LoggerFactory.getLogger(OssUploadHandler.class);
    private final OssService ossService;

    public OssUploadHandler(OssService ossService) {
        this.ossService = ossService;
    }

    @Override
    public boolean execute(FlowNodeDefinition node, FlowContext context) {
        if (ossService == null || !ossService.isEnabled()) {
            logger.info("OSS未配置或未启用，跳过上传");
            return true;
        }

        Map<String, Object> cfg = node.getConfig();
        String filePath = null;
        if (cfg != null && cfg.get("filePath") instanceof String) {
            filePath = (String) cfg.get("filePath");
        }
        if (filePath == null && context.getVariables().get("capturePath") instanceof String) {
            filePath = (String) context.getVariables().get("capturePath");
        }
        if (filePath == null && context.getVariables().get("recordFilePath") instanceof String) {
            filePath = (String) context.getVariables().get("recordFilePath");
        }

        if (filePath == null) {
            logger.warn("未找到可上传的文件路径");
            return false;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            logger.warn("上传文件不存在: {}", filePath);
            return false;
        }

        String template = cfg != null && cfg.get("path") instanceof String
                ? (String) cfg.get("path")
                : "alarm/{deviceId}/" + DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now()) + "/{fileName}";

        Map<String, Object> extra = new HashMap<>();
        extra.put("fileName", file.getName());

        String objectKey = HandlerUtils.renderTemplate(template, context, extra);
        String url = ossService.uploadFile(filePath, objectKey);
        if (url != null) {
            context.putVariable("ossUrl", url);
            context.putVariable("captureUrl", url);
            logger.info("OSS上传成功: {}", url);
            return true;
        }
        logger.warn("OSS上传失败: {}", filePath);
        return false;
    }
}
