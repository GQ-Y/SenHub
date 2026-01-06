package command

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"time"

	"hikvision-nvr-control/internal/config"
	"hikvision-nvr-control/internal/device"
	"hikvision-nvr-control/internal/hikvision"
	"hikvision-nvr-control/internal/mqtt"
	"hikvision-nvr-control/internal/oss"
)

// Handler 命令处理器
type Handler struct {
	config   *config.Config
	manager  *device.Manager
	sdk      *hikvision.SDK
	mqtt     *mqtt.Client
	oss      *oss.Client
}

// CommandRequest 命令请求
type CommandRequest struct {
	Command   string                 `json:"command"`
	DeviceID  string                 `json:"device_id"`
	RequestID string                 `json:"request_id"`
	Data      map[string]interface{} `json:"data,omitempty"`
}

// CommandResponse 命令响应
type CommandResponse struct {
	RequestID string      `json:"request_id"`
	DeviceID  string      `json:"device_id"`
	Command   string      `json:"command"`
	Success   bool        `json:"success"`
	Data      interface{} `json:"data,omitempty"`
	Error     string      `json:"error,omitempty"`
	Timestamp int64       `json:"timestamp"`
}

// NewHandler 创建命令处理器
func NewHandler(cfg *config.Config, mgr *device.Manager, sdk *hikvision.SDK, mqttClient *mqtt.Client, ossClient *oss.Client) *Handler {
	return &Handler{
		config:  cfg,
		manager: mgr,
		sdk:     sdk,
		mqtt:    mqttClient,
		oss:     ossClient,
	}
}

// HandleCommand 处理命令
func (h *Handler) HandleCommand(topic string, payload []byte) error {
	var req CommandRequest
	if err := json.Unmarshal(payload, &req); err != nil {
		return fmt.Errorf("解析命令失败: %w", err)
	}
	
	log.Printf("处理命令: %s, 设备: %s, 请求ID: %s", req.Command, req.DeviceID, req.RequestID)
	
	// 创建响应
	response := CommandResponse{
		RequestID: req.RequestID,
		DeviceID: req.DeviceID,
		Command:  req.Command,
		Success:  false,
		Timestamp: time.Now().Unix(),
	}
	
	// 根据命令类型处理
	var err error
	switch req.Command {
	case "capture":
		err = h.handleCapture(&req, &response)
	case "reboot":
		err = h.handleReboot(&req, &response)
	case "playback":
		err = h.handlePlayback(&req, &response)
	case "play_audio":
		err = h.handlePlayAudio(&req, &response)
	case "ptz_control":
		err = h.handlePTZControl(&req, &response)
	default:
		response.Error = fmt.Sprintf("未知命令: %s", req.Command)
	}
	
	if err != nil {
		response.Error = err.Error()
	}
	
	// 发送响应
	return h.mqtt.PublishResponse(response)
}

// handleCapture 处理抓图命令
func (h *Handler) handleCapture(req *CommandRequest, resp *CommandResponse) error {
	handle, err := h.manager.GetDevice(req.DeviceID)
	if err != nil {
		return fmt.Errorf("设备未登录: %w", err)
	}
	
	channel := handle.Device.StartChannel
	if ch, ok := req.Data["channel"].(float64); ok {
		channel = int(ch)
	}
	
	// 创建临时文件
	tmpFile := fmt.Sprintf("/tmp/capture_%s_%d.jpg", req.DeviceID, time.Now().Unix())
	defer os.Remove(tmpFile)
	
	// 抓图
	if err := h.sdk.CapturePicture(handle.UserID, channel, tmpFile); err != nil {
		return fmt.Errorf("抓图失败: %w", err)
	}
	
	// 读取图片并转换为base64
	imageData, err := os.ReadFile(tmpFile)
	if err != nil {
		return fmt.Errorf("读取图片失败: %w", err)
	}
	
	base64Image := base64.StdEncoding.EncodeToString(imageData)
	
	resp.Success = true
	resp.Data = map[string]interface{}{
		"image": base64Image,
		"format": "jpeg",
		"channel": channel,
	}
	
	return nil
}

// handleReboot 处理重启命令
func (h *Handler) handleReboot(req *CommandRequest, resp *CommandResponse) error {
	handle, err := h.manager.GetDevice(req.DeviceID)
	if err != nil {
		return fmt.Errorf("设备未登录: %w", err)
	}
	
	if err := h.sdk.RebootDevice(handle.UserID); err != nil {
		return fmt.Errorf("重启设备失败: %w", err)
	}
	
	resp.Success = true
	resp.Data = map[string]interface{}{
		"message": "设备重启中",
	}
	
	return nil
}

// handlePlayback 处理录像回放命令
func (h *Handler) handlePlayback(req *CommandRequest, resp *CommandResponse) error {
	handle, err := h.manager.GetDevice(req.DeviceID)
	if err != nil {
		return fmt.Errorf("设备未登录: %w", err)
	}
	
	startTime, ok1 := req.Data["start_time"].(string)
	endTime, ok2 := req.Data["end_time"].(string)
	if !ok1 || !ok2 {
		return fmt.Errorf("缺少时间参数")
	}
	
	// 解析时间
	start, err := time.Parse("2006-01-02 15:04:05", startTime)
	if err != nil {
		return fmt.Errorf("解析开始时间失败: %w", err)
	}
	
	end, err := time.Parse("2006-01-02 15:04:05", endTime)
	if err != nil {
		return fmt.Errorf("解析结束时间失败: %w", err)
	}
	
	// TODO: 实现录像回放下载
	// 这里需要调用SDK的录像回放接口，下载录像文件，然后上传到OSS
	
	resp.Success = true
	resp.Data = map[string]interface{}{
		"message": "录像回放功能待实现",
		"start_time": startTime,
		"end_time": endTime,
	}
	
	return nil
}

// handlePlayAudio 处理播放声音命令
func (h *Handler) handlePlayAudio(req *CommandRequest, resp *CommandResponse) error {
	handle, err := h.manager.GetDevice(req.DeviceID)
	if err != nil {
		return fmt.Errorf("设备未登录: %w", err)
	}
	
	audioData, ok := req.Data["audio_data"].(string)
	if !ok {
		return fmt.Errorf("缺少音频数据")
	}
	
	// 解码base64音频数据
	audioBytes, err := base64.StdEncoding.DecodeString(audioData)
	if err != nil {
		return fmt.Errorf("解码音频数据失败: %w", err)
	}
	
	// TODO: 实现播放声音功能
	// 这里需要调用SDK的语音对讲接口
	
	resp.Success = true
	resp.Data = map[string]interface{}{
		"message": "播放声音功能待实现",
		"audio_size": len(audioBytes),
	}
	
	return nil
}

// handlePTZControl 处理云台控制命令
func (h *Handler) handlePTZControl(req *CommandRequest, resp *CommandResponse) error {
	handle, err := h.manager.GetDevice(req.DeviceID)
	if err != nil {
		return fmt.Errorf("设备未登录: %w", err)
	}
	
	action, ok := req.Data["action"].(string)
	if !ok {
		return fmt.Errorf("缺少动作参数")
	}
	
	speed := 5
	if s, ok := req.Data["speed"].(float64); ok {
		speed = int(s)
	}
	
	channel := handle.Device.StartChannel
	if ch, ok := req.Data["channel"].(float64); ok {
		channel = int(ch)
	}
	
	// 映射动作到SDK命令
	var command int
	switch action {
	case "up":
		command = hikvision.PTZ_UP
	case "down":
		command = hikvision.PTZ_DOWN
	case "left":
		command = hikvision.PTZ_LEFT
	case "right":
		command = hikvision.PTZ_RIGHT
	case "zoom_in":
		command = hikvision.PTZ_ZOOM_IN
	case "zoom_out":
		command = hikvision.PTZ_ZOOM_OUT
	default:
		return fmt.Errorf("未知动作: %s", action)
	}
	
	// 执行云台控制
	if err := h.sdk.PTZControl(handle.UserID, channel, command, speed); err != nil {
		return fmt.Errorf("云台控制失败: %w", err)
	}
	
	resp.Success = true
	resp.Data = map[string]interface{}{
		"action": action,
		"speed": speed,
		"channel": channel,
	}
	
	return nil
}
