package com.digital.video.gateway.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.PutObjectRequest;
import com.digital.video.gateway.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * OSS上传服务
 * 用于将抓图文件上传到阿里云OSS
 */
public class OssService {
    private static final Logger logger = LoggerFactory.getLogger(OssService.class);
    
    private Config.OssConfig ossConfig;
    private OSS ossClient;
    private boolean enabled = false;
    
    public OssService(Config.OssConfig ossConfig) {
        this.ossConfig = ossConfig;
        if (ossConfig != null && ossConfig.isEnabled()) {
            init();
        }
    }
    
    /**
     * 初始化OSS客户端
     */
    private void init() {
        if (ossConfig == null || !ossConfig.isEnabled()) {
            logger.debug("OSS服务未启用");
            return;
        }
        
        try {
            String endpoint = ossConfig.getEndpoint();
            String accessKeyId = ossConfig.getAccessKeyId();
            String accessKeySecret = ossConfig.getAccessKeySecret();
            
            if (endpoint == null || endpoint.isEmpty() ||
                accessKeyId == null || accessKeyId.isEmpty() ||
                accessKeySecret == null || accessKeySecret.isEmpty()) {
                logger.warn("OSS配置不完整，OSS服务未启用");
                return;
            }
            
            ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
            enabled = true;
            logger.info("OSS服务初始化成功: endpoint={}, bucket={}", endpoint, ossConfig.getBucketName());
        } catch (Exception e) {
            logger.error("OSS服务初始化失败", e);
            enabled = false;
        }
    }
    
    /**
     * 检查OSS服务是否启用
     */
    public boolean isEnabled() {
        return enabled && ossClient != null;
    }
    
    /**
     * 上传文件到OSS
     * @param localFilePath 本地文件路径
     * @param objectKey OSS对象键（文件在OSS中的路径）
     * @return OSS文件URL，失败返回null
     */
    public String uploadFile(String localFilePath, String objectKey) {
        if (!isEnabled()) {
            logger.debug("OSS服务未启用，跳过上传: {}", localFilePath);
            return null;
        }
        
        if (ossConfig.getBucketName() == null || ossConfig.getBucketName().isEmpty()) {
            logger.warn("OSS Bucket名称未配置");
            return null;
        }
        
        File file = new File(localFilePath);
        if (!file.exists()) {
            logger.warn("文件不存在，无法上传: {}", localFilePath);
            return null;
        }
        
        try {
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                ossConfig.getBucketName(),
                objectKey,
                file
            );
            
            ossClient.putObject(putObjectRequest);
            
            // 生成文件URL
            String url = "https://" + ossConfig.getBucketName() + "." + 
                ossConfig.getEndpoint() + "/" + objectKey;
            
            logger.info("文件上传OSS成功: localPath={}, ossKey={}, url={}", 
                localFilePath, objectKey, url);
            
            return url;
        } catch (Exception e) {
            logger.error("上传文件到OSS失败: localPath={}, ossKey={}", localFilePath, objectKey, e);
            return null;
        }
    }
    
    /**
     * 更新OSS配置
     */
    public void updateConfig(Config.OssConfig newConfig) {
        // 关闭旧的客户端
        if (ossClient != null) {
            try {
                ossClient.shutdown();
            } catch (Exception e) {
                logger.warn("关闭OSS客户端失败", e);
            }
        }
        
        this.ossConfig = newConfig;
        enabled = false;
        ossClient = null;
        
        // 如果新配置启用，重新初始化
        if (newConfig != null && newConfig.isEnabled()) {
            init();
        }
    }
    
    /**
     * 关闭OSS服务
     */
    public void shutdown() {
        if (ossClient != null) {
            try {
                ossClient.shutdown();
                logger.info("OSS服务已关闭");
            } catch (Exception e) {
                logger.warn("关闭OSS服务失败", e);
            }
            ossClient = null;
        }
        enabled = false;
    }
}
