package device

import (
	"fmt"
	"sync"
	"time"

	"hikvision-nvr-control/internal/config"
	"hikvision-nvr-control/internal/hikvision"
)

// Manager 设备管理器
type Manager struct {
	config     *config.Config
	sdk        *hikvision.SDK
	repository DeviceRepository
	devices    map[string]*DeviceHandle // 设备句柄映射
	mu         sync.RWMutex
}

// DeviceHandle 设备句柄
type DeviceHandle struct {
	Device   *Device
	UserID   int
	LastPing time.Time
	mu       sync.RWMutex
}

// NewManager 创建设备管理器
func NewManager(cfg *config.Config, sdk *hikvision.SDK, repo DeviceRepository) *Manager {
	return &Manager{
		config:     cfg,
		sdk:        sdk,
		repository: repo,
		devices:    make(map[string]*DeviceHandle),
	}
}

// AddOrUpdateDevice 添加或更新设备
func (m *Manager) AddOrUpdateDevice(device *Device) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	
	// 检查设备是否已存在
	existing, err := m.repository.GetByID(device.DeviceID)
	if err == nil {
		// 更新现有设备
		device.ID = existing.ID
		device.CreatedAt = existing.CreatedAt
		if err := m.repository.Update(device); err != nil {
			return fmt.Errorf("更新设备失败: %w", err)
		}
	} else {
		// 创建新设备
		if err := m.repository.Create(device); err != nil {
			return fmt.Errorf("创建设备失败: %w", err)
		}
	}
	
	return nil
}

// LoginDevice 登录设备
func (m *Manager) LoginDevice(deviceID string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	
	device, err := m.repository.GetByID(deviceID)
	if err != nil {
		return fmt.Errorf("设备不存在: %w", err)
	}
	
	// 如果已经登录，先登出
	if handle, exists := m.devices[deviceID]; exists && handle.UserID >= 0 {
		m.sdk.Logout(handle.UserID)
	}
	
	// 登录设备
	loginInfo := &hikvision.LoginInfo{
		IP:       device.IP,
		Port:     device.Port,
		Username: device.Username,
		Password: device.Password,
	}
	
	userID, devInfo, err := m.sdk.Login(loginInfo)
	if err != nil {
		device.Status = "offline"
		device.UserID = -1
		now := time.Now()
		device.LastOffline = &now
		m.repository.Update(device)
		return fmt.Errorf("登录失败: %w", err)
	}
	
	// 更新设备信息
	device.UserID = userID
	device.Status = "online"
	device.SerialNumber = devInfo.SerialNumber
	device.DeviceType = devInfo.DeviceType
	device.Channels = devInfo.Channels
	device.StartChannel = devInfo.StartChannel
	now := time.Now()
	device.LastOnline = &now
	device.OfflineCount = 0
	
	// 生成RTSP URL
	device.RTSPURL = fmt.Sprintf("rtsp://%s:%d/Streaming/Channels/%d01", 
		device.IP, 554, device.StartChannel)
	
	if err := m.repository.Update(device); err != nil {
		m.sdk.Logout(userID)
		return fmt.Errorf("更新设备信息失败: %w", err)
	}
	
	// 保存句柄
	m.devices[deviceID] = &DeviceHandle{
		Device:   device,
		UserID:   userID,
		LastPing: time.Now(),
	}
	
	return nil
}

// LogoutDevice 登出设备
func (m *Manager) LogoutDevice(deviceID string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	
	handle, exists := m.devices[deviceID]
	if !exists {
		return nil
	}
	
	if handle.UserID >= 0 {
		m.sdk.Logout(handle.UserID)
	}
	
	device := handle.Device
	device.Status = "offline"
	device.UserID = -1
	now := time.Now()
	device.LastOffline = &now
	m.repository.Update(device)
	
	delete(m.devices, deviceID)
	return nil
}

// GetDevice 获取设备
func (m *Manager) GetDevice(deviceID string) (*DeviceHandle, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()
	
	handle, exists := m.devices[deviceID]
	if !exists {
		return nil, fmt.Errorf("设备未登录: %s", deviceID)
	}
	
	return handle, nil
}

// CheckDeviceStatus 检查设备状态
func (m *Manager) CheckDeviceStatus(deviceID string) error {
	device, err := m.repository.GetByID(deviceID)
	if err != nil {
		return err
	}
	
	// 如果设备已登录，尝试ping
	if device.UserID >= 0 {
		// 这里可以调用SDK的某个接口来检查连接状态
		// 暂时通过重新登录来检查
		return m.LoginDevice(deviceID)
	}
	
	return m.LoginDevice(deviceID)
}

// GetAllDevices 获取所有设备
func (m *Manager) GetAllDevices() ([]*Device, error) {
	return m.repository.ListAll()
}

// GetOnlineDevices 获取在线设备
func (m *Manager) GetOnlineDevices() ([]*Device, error) {
	return m.repository.ListOnline()
}

// UpdateDeviceStatus 更新设备状态
func (m *Manager) UpdateDeviceStatus(deviceID, status string) error {
	device, err := m.repository.GetByID(deviceID)
	if err != nil {
		return err
	}
	
	device.Status = status
	now := time.Now()
	if status == "online" {
		device.LastOnline = &now
		device.OfflineCount = 0
	} else {
		device.LastOffline = &now
		device.OfflineCount++
	}
	
	return m.repository.Update(device)
}
