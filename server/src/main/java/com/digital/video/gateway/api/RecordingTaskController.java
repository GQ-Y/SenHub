package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.database.RecordingTask;
import com.digital.video.gateway.service.RecordingTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

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
    public String getRecordingTasks(Request request, Response response) {
        try {
            String deviceId = request.queryParams("deviceId");
            String status = request.queryParams("status");
            List<RecordingTask> tasks = recordingTaskService.getRecordingTasks(deviceId, status);
            List<Map<String, Object>> result = tasks.stream()
                .map(RecordingTask::toMap)
                .collect(java.util.stream.Collectors.toList());
            
            response.status(200);
            return createSuccessResponse(result);
        } catch (Exception e) {
            logger.error("获取录像任务列表失败", e);
            response.status(500);
            return createErrorResponse(500, "获取录像任务列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取录像任务详情
     * GET /api/recording-tasks/:taskId
     */
    public String getRecordingTask(Request request, Response response) {
        try {
            String taskId = request.params(":taskId");
            RecordingTask task = recordingTaskService.getRecordingTask(taskId);
            if (task == null) {
                response.status(404);
                return createErrorResponse(404, "录像任务不存在");
            }
            
            response.status(200);
            return createSuccessResponse(task.toMap());
        } catch (Exception e) {
            logger.error("获取录像任务详情失败", e);
            response.status(500);
            return createErrorResponse(500, "获取录像任务详情失败: " + e.getMessage());
        }
    }

    /**
     * 创建录像任务
     * POST /api/recording-tasks
     */
    public String createRecordingTask(Request request, Response response) {
        try {
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            RecordingTask task = new RecordingTask();
            task.setDeviceId((String) body.get("deviceId"));
            task.setChannel((Integer) body.getOrDefault("channel", 1));
            task.setStartTime((String) body.get("startTime"));
            task.setEndTime((String) body.get("endTime"));
            task.setStatus((String) body.getOrDefault("status", "pending"));
            task.setProgress((Integer) body.getOrDefault("progress", 0));
            
            RecordingTask created = recordingTaskService.createRecordingTask(task);
            if (created == null) {
                response.status(500);
                return createErrorResponse(500, "创建录像任务失败");
            }
            
            response.status(201);
            return createSuccessResponse(created.toMap());
        } catch (Exception e) {
            logger.error("创建录像任务失败", e);
            response.status(500);
            return createErrorResponse(500, "创建录像任务失败: " + e.getMessage());
        }
    }

    /**
     * 更新录像任务
     * PUT /api/recording-tasks/:taskId
     */
    public String updateRecordingTask(Request request, Response response) {
        try {
            String taskId = request.params(":taskId");
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            RecordingTask task = new RecordingTask();
            task.setLocalFilePath((String) body.get("localFilePath"));
            task.setOssUrl((String) body.get("ossUrl"));
            task.setStatus((String) body.get("status"));
            task.setProgress((Integer) body.get("progress"));
            if (body.get("downloadHandle") != null) {
                task.setDownloadHandle(((Number) body.get("downloadHandle")).intValue());
            }
            task.setErrorMessage((String) body.get("errorMessage"));
            
            RecordingTask updated = recordingTaskService.updateRecordingTask(taskId, task);
            if (updated == null) {
                response.status(404);
                return createErrorResponse(404, "录像任务不存在");
            }
            
            response.status(200);
            return createSuccessResponse(updated.toMap());
        } catch (Exception e) {
            logger.error("更新录像任务失败", e);
            response.status(500);
            return createErrorResponse(500, "更新录像任务失败: " + e.getMessage());
        }
    }

    /**
     * 下载指定时间段录像
     * POST /api/recording-tasks/download
     */
    public String downloadRecording(Request request, Response response) {
        try {
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            String deviceId = (String) body.get("deviceId");
            Integer channel = (Integer) body.getOrDefault("channel", 1);
            String startTime = (String) body.get("startTime");
            String endTime = (String) body.get("endTime");
            
            if (deviceId == null || startTime == null || endTime == null) {
                response.status(400);
                return createErrorResponse(400, "参数不完整");
            }
            
            RecordingTask task = recordingTaskService.downloadRecording(deviceId, channel, startTime, endTime);
            if (task == null) {
                response.status(500);
                return createErrorResponse(500, "创建下载任务失败");
            }
            
            response.status(201);
            return createSuccessResponse(task.toMap());
        } catch (Exception e) {
            logger.error("创建下载任务失败", e);
            response.status(500);
            return createErrorResponse(500, "创建下载任务失败: " + e.getMessage());
        }
    }

    /**
     * 下载录像文件
     * GET /api/recording-tasks/:taskId/file
     */
    public String downloadRecordingFile(Request request, Response response) {
        try {
            String taskId = request.params(":taskId");
            RecordingTask task = recordingTaskService.getRecordingTask(taskId);
            if (task == null) {
                response.status(404);
                return createErrorResponse(404, "录像任务不存在");
            }
            
            String filePath = task.getLocalFilePath();
            if (filePath == null || filePath.isEmpty()) {
                response.status(404);
                return createErrorResponse(404, "录像文件不存在");
            }
            
            File file = new File(filePath);
            if (!file.exists()) {
                response.status(404);
                return createErrorResponse(404, "录像文件不存在");
            }
            
            response.type("video/mp4");
            response.header("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
            response.raw().setContentLengthLong(file.length());
            
            Files.copy(Paths.get(filePath), response.raw().getOutputStream());
            return "";
        } catch (Exception e) {
            logger.error("下载录像文件失败", e);
            response.status(500);
            return createErrorResponse(500, "下载录像文件失败: " + e.getMessage());
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
