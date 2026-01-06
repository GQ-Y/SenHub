package device

import (
	"time"

	"gorm.io/gorm"
)

// Device 设备模型
type Device struct {
	ID            uint      `gorm:"primarykey" json:"id"`
	CreatedAt     time.Time `json:"created_at"`
	UpdatedAt     time.Time `json:"updated_at"`
	
	DeviceID      string    `gorm:"uniqueIndex;not null" json:"device_id"` // IP地址作为设备ID
	SerialNumber  string    `gorm:"index" json:"serial_number"`            // 设备序列号
	Name          string    `json:"name"`                                   // 设备名称
	IP            string    `gorm:"not null" json:"ip"`                      // IP地址
	Port          int       `gorm:"default:8000" json:"port"`               // 端口
	Username      string    `json:"username"`                               // 用户名
	Password      string    `json:"password"`                               // 密码（加密存储）
	DeviceType    int       `json:"device_type"`                            // 设备类型
	Channels      int       `json:"channels"`                              // 通道数
	StartChannel  int       `json:"start_channel"`                          // 起始通道
	Status        string    `gorm:"default:offline" json:"status"`          // 状态: online/offline
	LastOnline    *time.Time `json:"last_online"`                          // 最后在线时间
	LastOffline   *time.Time `json:"last_offline"`                          // 最后离线时间
	UserID        int       `gorm:"default:-1" json:"user_id"`              // SDK登录返回的UserID
	RTSPURL       string    `json:"rtsp_url"`                              // RTSP地址
	OfflineCount  int       `gorm:"default:0" json:"offline_count"`        // 离线计数
}

// TableName 指定表名
func (Device) TableName() string {
	return "devices"
}

// DeviceRepository 设备仓储接口
type DeviceRepository interface {
	Create(device *Device) error
	Update(device *Device) error
	GetByID(deviceID string) (*Device, error)
	GetByIP(ip string) (*Device, error)
	GetBySerialNumber(serialNumber string) (*Device, error)
	ListAll() ([]*Device, error)
	ListOnline() ([]*Device, error)
	Delete(deviceID string) error
}

// deviceRepository 设备仓储实现
type deviceRepository struct {
	db *gorm.DB
}

// NewDeviceRepository 创建设备仓储
func NewDeviceRepository(db *gorm.DB) DeviceRepository {
	return &deviceRepository{db: db}
}

// Create 创建设备
func (r *deviceRepository) Create(device *Device) error {
	return r.db.Create(device).Error
}

// Update 更新设备
func (r *deviceRepository) Update(device *Device) error {
	return r.db.Save(device).Error
}

// GetByID 根据设备ID获取
func (r *deviceRepository) GetByID(deviceID string) (*Device, error) {
	var device Device
	err := r.db.Where("device_id = ?", deviceID).First(&device).Error
	if err != nil {
		return nil, err
	}
	return &device, nil
}

// GetByIP 根据IP获取
func (r *deviceRepository) GetByIP(ip string) (*Device, error) {
	var device Device
	err := r.db.Where("ip = ?", ip).First(&device).Error
	if err != nil {
		return nil, err
	}
	return &device, nil
}

// GetBySerialNumber 根据序列号获取
func (r *deviceRepository) GetBySerialNumber(serialNumber string) (*Device, error) {
	var device Device
	err := r.db.Where("serial_number = ?", serialNumber).First(&device).Error
	if err != nil {
		return nil, err
	}
	return &device, nil
}

// ListAll 列出所有设备
func (r *deviceRepository) ListAll() ([]*Device, error) {
	var devices []*Device
	err := r.db.Find(&devices).Error
	return devices, err
}

// ListOnline 列出在线设备
func (r *deviceRepository) ListOnline() ([]*Device, error) {
	var devices []*Device
	err := r.db.Where("status = ?", "online").Find(&devices).Error
	return devices, err
}

// Delete 删除设备
func (r *deviceRepository) Delete(deviceID string) error {
	return r.db.Where("device_id = ?", deviceID).Delete(&Device{}).Error
}
