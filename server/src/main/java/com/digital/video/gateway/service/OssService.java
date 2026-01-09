package com.digital.video.gateway.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.PutObjectRequest;
import com.digital.video.gateway.config.Config;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.UploadObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * OSS上传服务
 * 支持阿里云 OSS 和 Minio 对象存储
 */
public class OssService {
    private static final Logger logger = LoggerFactory.getLogger(OssService.class);

    private Config.OssConfig ossConfig;
    private OSS aliyunClient;
    private MinioClient minioClient;
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
            String type = ossConfig.getType();
            String endpoint = ossConfig.getEndpoint();
            String accessKeyId = ossConfig.getAccessKeyId();
            String accessKeySecret = ossConfig.getAccessKeySecret();

            if (endpoint == null || endpoint.isEmpty() ||
                    accessKeyId == null || accessKeyId.isEmpty() ||
                    accessKeySecret == null || accessKeySecret.isEmpty()) {
                logger.warn("{} 配置不完整，服务未启用", type);
                return;
            }

            if ("minio".equalsIgnoreCase(type)) {
                minioClient = MinioClient.builder()
                        .endpoint(endpoint)
                        .credentials(accessKeyId, accessKeySecret)
                        .build();

                // 确保 Bucket 存在
                boolean found = minioClient
                        .bucketExists(BucketExistsArgs.builder().bucket(ossConfig.getBucketName()).build());
                if (!found) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(ossConfig.getBucketName()).build());
                    logger.info("Minio Bucket 不存在，已自动创建: {}", ossConfig.getBucketName());
                }
                logger.info("Minio 服务初始化成功: endpoint={}, bucket={}", endpoint, ossConfig.getBucketName());
            } else {
                aliyunClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
                logger.info("阿里云 OSS 服务初始化成功: endpoint={}, bucket={}", endpoint, ossConfig.getBucketName());
            }

            enabled = true;
        } catch (Exception e) {
            logger.error("OSS服务初始化失败", e);
            enabled = false;
        }
    }

    /**
     * 检查OSS服务是否启用
     */
    public boolean isEnabled() {
        return enabled && (aliyunClient != null || minioClient != null);
    }

    /**
     * 上传文件到OSS/Minio
     * 
     * @param localFilePath 本地文件路径
     * @param objectKey     对象键（文件在存储中的路径）
     * @return 文件URL，失败返回null
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
            if (minioClient != null) {
                // Minio 上传
                minioClient.uploadObject(
                        UploadObjectArgs.builder()
                                .bucket(ossConfig.getBucketName())
                                .object(objectKey)
                                .filename(localFilePath)
                                .build());

                // 生成 Minio URL (假设是 public 访问或按需构造)
                String url = ossConfig.getEndpoint() + "/" + ossConfig.getBucketName() + "/" + objectKey;
                if (!url.startsWith("http")) {
                    url = "http://" + url;
                }
                logger.info("文件上传 Minio 成功: localPath={}, key={}, url={}", localFilePath, objectKey, url);
                return url;
            } else {
                // 阿里云 OSS 上传
                PutObjectRequest putObjectRequest = new PutObjectRequest(
                        ossConfig.getBucketName(),
                        objectKey,
                        file);
                aliyunClient.putObject(putObjectRequest);

                String url = "https://" + ossConfig.getBucketName() + "." +
                        ossConfig.getEndpoint() + "/" + objectKey;
                logger.info("文件上传 阿里云 OSS 成功: localPath={}, key={}, url={}", localFilePath, objectKey, url);
                return url;
            }
        } catch (Exception e) {
            logger.error("上传文件到 OSS/Minio 失败: localPath={}, key={}", localFilePath, objectKey, e);
            return null;
        }
    }

    /**
     * 更新OSS配置
     */
    public void updateConfig(Config.OssConfig newConfig) {
        shutdown();
        this.ossConfig = newConfig;
        if (newConfig != null && newConfig.isEnabled()) {
            init();
        }
    }

    /**
     * 关闭OSS服务
     */
    public void shutdown() {
        if (aliyunClient != null) {
            try {
                aliyunClient.shutdown();
                logger.info("阿里云 OSS 服务已关闭");
            } catch (Exception e) {
                logger.warn("关闭阿里云 OSS 服务失败", e);
            }
            aliyunClient = null;
        }
        minioClient = null;
        enabled = false;
    }
}
