package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.service.OssService;
import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowNodeDefinition;
import com.digital.video.gateway.workflow.FlowNodeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * OSS上传节点
 * 
 * 自定义OSS上传规则：
 * - 图片文件（抓图）：使用 base64 上传 (/upload/file_upload/imgUpload)
 * - 视频文件：使用 form-data 上传 (/upload/file_upload/formDataUpload)
 */
public class OssUploadHandler implements FlowNodeHandler {
    private static final Logger logger = LoggerFactory.getLogger(OssUploadHandler.class);
    private final OssService ossService;
    
    // 图片扩展名
    private static final String[] IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp"};
    // 视频扩展名
    private static final String[] VIDEO_EXTENSIONS = {".mp4", ".avi", ".mkv", ".mov", ".wmv", ".flv", ".ts", ".m3u8"};

    public OssUploadHandler(OssService ossService) {
        this.ossService = ossService;
    }

    @Override
    public boolean execute(FlowNodeDefinition node, FlowContext context) {
        if (ossService == null) {
            logger.info("OSS服务未初始化，跳过上传。请在系统设置中配置OSS服务");
            return true;
        }
        if (!ossService.isEnabled()) {
            logger.info("OSS服务未启用（enabled=false），跳过上传。请在系统设置中启用OSS服务");
            return true;
        }

        Map<String, Object> cfg = node.getConfig();
        String filePath = null;
        boolean isCapture = false;  // 标记是否是抓图
        
        // 优先从节点配置获取
        if (cfg != null && cfg.get("filePath") instanceof String) {
            filePath = (String) cfg.get("filePath");
        }
        // 其次从上下文获取抓图路径
        if (filePath == null && context.getVariables().get("capturePath") instanceof String) {
            filePath = (String) context.getVariables().get("capturePath");
            isCapture = true;
        }
        // 最后从上下文获取录像路径
        if (filePath == null && context.getVariables().get("recordFilePath") instanceof String) {
            filePath = (String) context.getVariables().get("recordFilePath");
            isCapture = false;
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
        String url = null;
        
        // 根据文件类型选择上传方式
        String fileName = file.getName().toLowerCase();
        boolean isImage = isImageFile(fileName) || isCapture;
        boolean isVideo = isVideoFile(fileName);
        
        logger.info("OSS上传类型判断: fileName={}, isCapture={}, isImage={}, isVideo={}", 
                fileName, isCapture, isImage, isVideo);
        
        try {
            if (isImage) {
                // 图片使用 base64 上传
                logger.info("使用Base64方式上传图片: {}", file.getName());
                byte[] fileData = Files.readAllBytes(file.toPath());
                url = ossService.uploadFileBase64(fileData, file.getName());
                if (url != null) {
                    logger.info("OSS Base64上传成功（图片）: {}", url);
                } else {
                    logger.warn("OSS Base64上传返回null");
                }
            } else if (isVideo) {
                // 视频使用 form-data 上传
                url = ossService.uploadFile(filePath, objectKey);
                if (url != null) {
                    logger.info("OSS FormData上传成功（视频）: {}", url);
                }
            } else {
                // 其他文件默认使用 form-data
                url = ossService.uploadFile(filePath, objectKey);
                if (url != null) {
                    logger.info("OSS FormData上传成功（其他）: {}", url);
                }
            }
        } catch (Exception e) {
            logger.error("OSS上传异常: filePath={}", filePath, e);
            return false;
        }
        
        if (url != null) {
            context.putVariable("ossUrl", url);
            // 根据文件类型设置不同的变量，便于webhook区分
            if (isImage) {
                context.putVariable("captureOssUrl", url);
                context.putVariable("captureUrl", url);  // 兼容旧逻辑
            } else if (isVideo) {
                context.putVariable("recordOssUrl", url);
                context.putVariable("videoUrl", url);
            }
            return true;
        }
        logger.warn("OSS上传失败: {}", filePath);
        return false;
    }
    
    /**
     * 判断是否是图片文件
     */
    private boolean isImageFile(String fileName) {
        for (String ext : IMAGE_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 判断是否是视频文件
     */
    private boolean isVideoFile(String fileName) {
        for (String ext : VIDEO_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
}
