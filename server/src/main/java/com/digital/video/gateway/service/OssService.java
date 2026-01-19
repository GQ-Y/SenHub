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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * OSS上传服务
 * 支持阿里云 OSS 和 Minio 对象存储
 */
public class OssService {
    private static final Logger logger = LoggerFactory.getLogger(OssService.class);

    private Config.OssConfig ossConfig;
    private OSS aliyunClient;
    private MinioClient minioClient;
    private HttpClient httpClient;
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

            if ("custom".equalsIgnoreCase(type)) {
                if (endpoint == null || endpoint.isEmpty()) {
                    logger.warn("自定义OSS配置不完整，endpoint缺失，服务未启用");
                    return;
                }
                httpClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(30))
                        .build();
                enabled = true;
                logger.info("自定义OSS 服务初始化成功: endpoint={}", endpoint);
                return;
            }

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
        return enabled && (aliyunClient != null || minioClient != null || httpClient != null);
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
            if (httpClient != null && isCustomType()) {
                return uploadFileFormData(localFilePath, objectKey);
            }

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
     * 自定义OSS：form-data上传文件
     */
    public String uploadFileFormData(String localFilePath, String objectKey) {
        if (!isEnabled() || httpClient == null || !isCustomType()) {
            return null;
        }
        File file = new File(localFilePath);
        if (!file.exists()) {
            logger.warn("文件不存在，无法上传: {}", localFilePath);
            return null;
        }

        String url = buildUrl(ossConfig.getEndpoint(), ossConfig.getFormDataUploadPath());
        String boundary = "----VideoGatewayBoundary" + System.currentTimeMillis();

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            // file part
            bos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            bos.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            bos.write(("Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            bos.write(Files.readAllBytes(file.toPath()));
            bos.write("\r\n".getBytes(StandardCharsets.UTF_8));

            // objectKey part (optional)
            if (objectKey != null && !objectKey.isEmpty()) {
                bos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                bos.write(("Content-Disposition: form-data; name=\"objectKey\"\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8));
                bos.write(objectKey.getBytes(StandardCharsets.UTF_8));
                bos.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }

            bos.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bos.toByteArray()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String resolvedUrl = extractUrlFromResponse(response.body(), objectKey, url);
                logger.info("文件上传 自定义OSS 成功: localPath={}, key={}, url={}", localFilePath, objectKey, resolvedUrl);
                return resolvedUrl;
            } else {
                logger.warn("自定义OSS上传失败: status={}, body={}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            logger.error("自定义OSS上传异常: localPath={}, key={}", localFilePath, objectKey, e);
        }
        return null;
    }

    /**
     * 自定义OSS：base64上传
     */
    public String uploadFileBase64(byte[] data, String fileName) {
        if (!isEnabled()) {
            return null;
        }

        // 如果是自定义类型走专用接口，否则回落到常规上传
        if (httpClient != null && isCustomType()) {
            String url = buildUrl(ossConfig.getEndpoint(), ossConfig.getBase64UploadPath());
            String base64 = Base64.getEncoder().encodeToString(data);
            String objectKey = fileName != null ? fileName : ("upload-" + System.currentTimeMillis());

            try {
                String payload = "{\"fileName\":\"" + escapeJson(fileName) + "\",\"data\":\"" + base64 + "\",\"objectKey\":\""
                        + escapeJson(objectKey) + "\"}";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(45))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    String resolvedUrl = extractUrlFromResponse(response.body(), objectKey, url);
                    logger.info("Base64上传 自定义OSS 成功: fileName={}, url={}", fileName, resolvedUrl);
                    return resolvedUrl;
                } else {
                    logger.warn("自定义OSS Base64上传失败: status={}, body={}", response.statusCode(), response.body());
                }
            } catch (Exception e) {
                logger.error("自定义OSS Base64上传异常: fileName={}", fileName, e);
            }
            return null;
        }

        // 默认回落：写入临时文件后复用常规上传
        try {
            Path temp = Files.createTempFile("oss-base64-", fileName != null ? fileName : "upload");
            Files.write(temp, data);
            String result = uploadFile(temp.toString(), fileName != null ? fileName : temp.getFileName().toString());
            Files.deleteIfExists(temp);
            return result;
        } catch (Exception e) {
            logger.error("Base64上传回落失败", e);
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
        httpClient = null;
        enabled = false;
    }

    private boolean isCustomType() {
        return ossConfig != null && "custom".equalsIgnoreCase(ossConfig.getType());
    }

    private String buildUrl(String endpoint, String path) {
        if (endpoint == null) {
            return path;
        }
        String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        String normalizedPath = (path != null && path.startsWith("/")) ? path : "/" + (path == null ? "" : path);
        return base + normalizedPath;
    }

    /**
     * 尝试从响应体中提取url字段，若失败则返回fallback
     */
    @SuppressWarnings("unchecked")
    private String extractUrlFromResponse(String responseBody, String objectKey, String fallbackUrl) {
        if (responseBody != null && !responseBody.isEmpty()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> map = mapper.readValue(responseBody, Map.class);
                Object url = map.get("url");
                if (url == null && map.get("data") instanceof Map) {
                    Map<String, Object> data = (Map<String, Object>) map.get("data");
                    url = data.get("url");
                    if (url == null) {
                        url = data.get("path");
                    }
                }
                if (url != null) {
                    return url.toString();
                }
            } catch (Exception e) {
                logger.debug("响应体解析失败，使用fallback: {}", e.getMessage());
            }
        }

        if (objectKey != null && !objectKey.isEmpty()) {
            String candidate = buildUrl(ossConfig.getEndpoint(), objectKey);
            if (candidate.startsWith("http")) {
                return candidate;
            }
            return "http://" + candidate;
        }
        return fallbackUrl;
    }

    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
