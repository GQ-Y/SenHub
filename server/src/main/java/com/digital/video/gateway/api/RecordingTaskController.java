package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.database.RecordingTask;
import com.digital.video.gateway.service.RecordingTaskService;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 录像任务控制器
 */
public class RecordingTaskController {
    private static final Logger logger = LoggerFactory.getLogger(RecordingTaskController.class);
    private final RecordingTaskService recordingTaskService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RecordingTaskController(RecordingTaskService recordingTaskService) {
        this.recordingTaskService = recordingTaskService;
    }

    /**
     * 获取录像任务列表
     * GET /api/recording-tasks
     */
    public void getRecordingTasks(Context ctx) {
        try {
            String deviceId = ctx.queryParam("deviceId");
            String status = ctx.queryParam("status");
            List<RecordingTask> tasks = recordingTaskService.getRecordingTasks(deviceId, status);
            List<Map<String, Object>> result = tasks.stream()
                    .map(RecordingTask::toMap)
                    .collect(java.util.stream.Collectors.toList());

            ctx.status(200);
            ctx.result(createSuccessResponse(result));
        } catch (Exception e) {
            logger.error("获取录像任务列表失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "获取录像任务列表失败: " + e.getMessage()));
        }
    }

    /**
     * 获取录像任务详情
     * GET /api/recording-tasks/:taskId
     */
    public void getRecordingTask(Context ctx) {
        try {
            String taskId = ctx.pathParam("taskId");
            RecordingTask task = recordingTaskService.getRecordingTask(taskId);
            if (task == null) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "录像任务不存在"));
                return;
            }

            ctx.status(200);
            ctx.result(createSuccessResponse(task.toMap()));
        } catch (Exception e) {
            logger.error("获取录像任务详情失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "获取录像任务详情失败: " + e.getMessage()));
        }
    }

    /**
     * 创建录像任务
     * POST /api/recording-tasks
     */
    public void createRecordingTask(Context ctx) {
        try {
            Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);
            RecordingTask task = new RecordingTask();
            task.setDeviceId((String) body.get("deviceId"));
            task.setChannel((Integer) body.getOrDefault("channel", 1));
            task.setStartTime((String) body.get("startTime"));
            task.setEndTime((String) body.get("endTime"));

            Object statusObj = body.getOrDefault("status", 0);
            if (statusObj instanceof Number) {
                task.setStatus(((Number) statusObj).intValue());
            } else if ("pending".equals(statusObj)) {
                task.setStatus(0);
            } else if ("downloading".equals(statusObj)) {
                task.setStatus(1);
            } else if ("completed".equals(statusObj)) {
                task.setStatus(2);
            } else if ("failed".equals(statusObj)) {
                task.setStatus(3);
            } else {
                task.setStatus(0);
            }
            task.setProgress((Integer) body.getOrDefault("progress", 0));

            RecordingTask created = recordingTaskService.createRecordingTask(task);
            if (created == null) {
                ctx.status(500);
                ctx.result(createErrorResponse(500, "创建录像任务失败"));
                return;
            }

            ctx.status(201);
            ctx.result(createSuccessResponse(created.toMap()));
        } catch (Exception e) {
            logger.error("创建录像任务失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "创建录像任务失败: " + e.getMessage()));
        }
    }

    /**
     * 更新录像任务
     * PUT /api/recording-tasks/:taskId
     */
    public void updateRecordingTask(Context ctx) {
        try {
            String taskId = ctx.pathParam("taskId");
            Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);
            RecordingTask task = new RecordingTask();
            task.setLocalFilePath((String) body.get("localFilePath"));
            task.setOssUrl((String) body.get("ossUrl"));

            Object statusObj = body.get("status");
            if (statusObj instanceof Number) {
                task.setStatus(((Number) statusObj).intValue());
            } else if ("pending".equals(statusObj)) {
                task.setStatus(0);
            } else if ("downloading".equals(statusObj)) {
                task.setStatus(1);
            } else if ("completed".equals(statusObj)) {
                task.setStatus(2);
            } else if ("failed".equals(statusObj)) {
                task.setStatus(3);
            }
            task.setProgress((Integer) body.get("progress"));
            if (body.get("downloadHandle") != null) {
                task.setDownloadHandle(((Number) body.get("downloadHandle")).intValue());
            }
            task.setErrorMessage((String) body.get("errorMessage"));

            RecordingTask updated = recordingTaskService.updateRecordingTask(taskId, task);
            if (updated == null) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "录像任务不存在"));
                return;
            }

            ctx.status(200);
            ctx.result(createSuccessResponse(updated.toMap()));
        } catch (Exception e) {
            logger.error("更新录像任务失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "更新录像任务失败: " + e.getMessage()));
        }
    }

    /**
     * 下载指定时间段录像
     * POST /api/recording-tasks/download
     */
    public void downloadRecording(Context ctx) {
        try {
            Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);
            String deviceId = (String) body.get("deviceId");
            Integer channel = (Integer) body.getOrDefault("channel", 1);
            String startTime = (String) body.get("startTime");
            String endTime = (String) body.get("endTime");

            if (deviceId == null || startTime == null || endTime == null) {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "参数不完整"));
                return;
            }

            RecordingTask task = recordingTaskService.downloadRecording(deviceId, channel, startTime, endTime);
            if (task == null) {
                ctx.status(500);
                ctx.result(createErrorResponse(500, "创建下载任务失败"));
                return;
            }

            ctx.status(201);
            ctx.result(createSuccessResponse(task.toMap()));
        } catch (Exception e) {
            logger.error("创建下载任务失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "创建下载任务失败: " + e.getMessage()));
        }
    }

    /**
     * 下载录像文件
     * GET /api/recording-tasks/:taskId/file
     */
    public void downloadRecordingFile(Context ctx) {
        try {
            String taskId = ctx.pathParam("taskId");
            RecordingTask task = recordingTaskService.getRecordingTask(taskId);
            if (task == null) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "录像任务不存在"));
                return;
            }

            String filePath = task.getLocalFilePath();
            if (filePath == null || filePath.isEmpty()) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "录像文件不存在"));
                return;
            }

            File file = new File(filePath);
            if (!file.exists()) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "录像文件不存在"));
                return;
            }

            ctx.contentType("video/mp4");
            ctx.header("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
            ctx.res().setContentLengthLong(file.length());

            Files.copy(Paths.get(filePath), ctx.res().getOutputStream());
        } catch (Exception e) {
            logger.error("下载录像文件失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "下载录像文件失败: " + e.getMessage()));
        }
    }

    private String createSuccessResponse(Object data) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "success");
            response.put("data", data);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            logger.error("创建响应失败", e);
            return "{\"code\":500,\"message\":\"Internal error\",\"data\":null}";
        }
    }

    private String createErrorResponse(int code, String message) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("code", code);
            response.put("message", message);
            response.put("data", null);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            logger.error("创建错误响应失败", e);
            return "{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}";
        }
    }
}
