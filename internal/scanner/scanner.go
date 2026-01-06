package scanner

import (
	"fmt"
	"log"
	"time"

	"hikvision-nvr-control/internal/config"
	"hikvision-nvr-control/internal/device"
	"hikvision-nvr-control/internal/hikvision"
)

// Scanner 设备扫描器
type Scanner struct {
	config   *config.Config
	sdk      *hikvision.SDK
	manager  *device.Manager
	listenHandle int
	stopChan chan struct{}
}

// NewScanner 创建扫描器
func NewScanner(cfg *config.Config, sdk *hikvision.SDK, mgr *device.Manager) *Scanner {
	return &Scanner{
		config:  cfg,
		sdk:     sdk,
		manager: mgr,
		stopChan: make(chan struct{}),
	}
}

// Start 启动扫描器
func (s *Scanner) Start() error {
	if !s.config.Scanner.Enabled {
		log.Println("设备扫描器已禁用")
		return nil
	}
	
	// 启动监听
	handle, err := s.sdk.StartListen(s.config.Scanner.ListenIP, s.config.Scanner.ListenPort)
	if err != nil {
		return fmt.Errorf("启动监听失败: %w", err)
	}
	
	s.listenHandle = handle
	log.Printf("设备扫描器已启动，监听地址: %s:%d", s.config.Scanner.ListenIP, s.config.Scanner.ListenPort)
	
	// 启动定期扫描
	go s.scanLoop()
	
	return nil
}

// Stop 停止扫描器
func (s *Scanner) Stop() {
	close(s.stopChan)
	if s.listenHandle > 0 {
		s.sdk.StopListen(s.listenHandle)
	}
	log.Println("设备扫描器已停止")
}

// scanLoop 扫描循环
func (s *Scanner) scanLoop() {
	ticker := time.NewTicker(time.Duration(s.config.Scanner.Interval) * time.Second)
	defer ticker.Stop()
	
	// 立即执行一次扫描
	s.scanDevices()
	
	for {
		select {
		case <-ticker.C:
			s.scanDevices()
		case <-s.stopChan:
			return
		}
	}
}

// scanDevices 扫描设备
func (s *Scanner) scanDevices() {
	log.Println("开始扫描设备...")
	
	// 从数据库加载所有设备
	devices, err := s.manager.GetAllDevices()
	if err != nil {
		log.Printf("获取设备列表失败: %v", err)
		return
	}
	
	// 尝试登录所有设备
	for _, dev := range devices {
		// 如果设备已在线，跳过
		if dev.Status == "online" {
			continue
		}
		
		// 尝试登录
		if err := s.manager.LoginDevice(dev.DeviceID); err != nil {
			log.Printf("设备 %s 登录失败: %v", dev.DeviceID, err)
			continue
		}
		
		log.Printf("设备 %s 登录成功", dev.DeviceID)
	}
	
	log.Println("设备扫描完成")
}

// OnDeviceDiscovered 设备发现回调（由SDK监听回调触发）
func (s *Scanner) OnDeviceDiscovered(ip string, port int, serialNumber string) {
	log.Printf("发现新设备: IP=%s, Port=%d, Serial=%s", ip, port, serialNumber)
	
	// 检查设备是否已存在
	deviceID := ip
	existing, err := s.manager.GetAllDevices()
	if err == nil {
		for _, dev := range existing {
			if dev.IP == ip || dev.SerialNumber == serialNumber {
				// 设备已存在，尝试登录
				if err := s.manager.LoginDevice(dev.DeviceID); err != nil {
					log.Printf("自动登录设备 %s 失败: %v", dev.DeviceID, err)
				}
				return
			}
		}
	}
	
	// 创建新设备
	newDevice := &device.Device{
		DeviceID:     deviceID,
		IP:           ip,
		Port:         port,
		SerialNumber: serialNumber,
		Username:     s.config.Device.DefaultUsername,
		Password:     s.config.Device.DefaultPassword,
		Status:       "offline",
	}
	
	if err := s.manager.AddOrUpdateDevice(newDevice); err != nil {
		log.Printf("添加设备失败: %v", err)
		return
	}
	
	// 尝试登录
	if err := s.manager.LoginDevice(deviceID); err != nil {
		log.Printf("自动登录新设备失败: %v", err)
	}
}
