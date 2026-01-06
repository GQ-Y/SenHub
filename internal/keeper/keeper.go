package keeper

import (
	"log"
	"time"

	"hikvision-nvr-control/internal/config"
	"hikvision-nvr-control/internal/device"
	"hikvision-nvr-control/internal/mqtt"
)

// Keeper 保活系统
type Keeper struct {
	config  *config.Config
	manager *device.Manager
	mqtt    *mqtt.Client
	stopChan chan struct{}
}

// NewKeeper 创建保活系统
func NewKeeper(cfg *config.Config, mgr *device.Manager, mqttClient *mqtt.Client) *Keeper {
	return &Keeper{
		config:   cfg,
		manager:  mgr,
		mqtt:     mqttClient,
		stopChan: make(chan struct{}),
	}
}

// Start 启动保活系统
func (k *Keeper) Start() error {
	if !k.config.Keeper.Enabled {
		log.Println("保活系统已禁用")
		return nil
	}
	
	log.Println("保活系统已启动")
	
	go k.keepAliveLoop()
	
	return nil
}

// Stop 停止保活系统
func (k *Keeper) Stop() {
	close(k.stopChan)
	log.Println("保活系统已停止")
}

// keepAliveLoop 保活循环
func (k *Keeper) keepAliveLoop() {
	ticker := time.NewTicker(time.Duration(k.config.Keeper.CheckInterval) * time.Second)
	defer ticker.Stop()
	
	for {
		select {
		case <-ticker.C:
			k.checkDevices()
		case <-k.stopChan:
			return
		}
	}
}

// checkDevices 检查设备状态
func (k *Keeper) checkDevices() {
	devices, err := k.manager.GetAllDevices()
	if err != nil {
		log.Printf("获取设备列表失败: %v", err)
		return
	}
	
	for _, device := range devices {
		k.checkDevice(device)
	}
}

// checkDevice 检查单个设备
func (k *Keeper) checkDevice(device *device.Device) {
	// 尝试检查设备状态
	err := k.manager.CheckDeviceStatus(device.DeviceID)
	
	if err != nil {
		// 检查失败，增加离线计数
		device.OfflineCount++
		
		// 如果达到阈值，标记为离线
		if device.OfflineCount >= k.config.Keeper.OfflineThreshold {
			if device.Status != "offline" {
				log.Printf("设备 %s 离线", device.DeviceID)
				k.manager.UpdateDeviceStatus(device.DeviceID, "offline")
				k.publishStatus(device)
			}
		}
	} else {
		// 检查成功，重置离线计数
		if device.Status != "online" {
			log.Printf("设备 %s 上线", device.DeviceID)
			k.manager.UpdateDeviceStatus(device.DeviceID, "online")
			k.publishStatus(device)
		}
		device.OfflineCount = 0
	}
}

// publishStatus 发布设备状态
func (k *Keeper) publishStatus(device *device.Device) {
	if !k.mqtt.IsConnected() {
		return
	}
	
	status := map[string]interface{}{
		"device_id": device.DeviceID,
		"status":    device.Status,
		"timestamp": time.Now().Unix(),
		"device_info": map[string]interface{}{
			"name":      device.Name,
			"ip":        device.IP,
			"port":      device.Port,
			"rtsp_url":  device.RTSPURL,
			"channels":  device.Channels,
		},
	}
	
	if err := k.mqtt.PublishStatus(status); err != nil {
		log.Printf("发布设备状态失败: %v", err)
	}
}
