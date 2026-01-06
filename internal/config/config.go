package config

import (
	"fmt"
	"os"

	"gopkg.in/yaml.v2"
)

// Config 应用配置结构
type Config struct {
	MQTT     MQTTConfig     `yaml:"mqtt"`
	Scanner  ScannerConfig  `yaml:"scanner"`
	Device   DeviceConfig   `yaml:"device"`
	Keeper   KeeperConfig   `yaml:"keeper"`
	OSS      OSSConfig      `yaml:"oss"`
	Database DatabaseConfig `yaml:"database"`
	Log      LogConfig      `yaml:"log"`
}

// MQTTConfig MQTT配置
type MQTTConfig struct {
	Broker        string `yaml:"broker"`
	ClientID      string `yaml:"client_id"`
	Username      string `yaml:"username"`
	Password      string `yaml:"password"`
	StatusTopic   string `yaml:"status_topic"`
	CommandTopic  string `yaml:"command_topic"`
	ResponseTopic string `yaml:"response_topic"`
	QoS           byte   `yaml:"qos"`
	KeepAlive     int    `yaml:"keep_alive"`
}

// ScannerConfig 扫描器配置
type ScannerConfig struct {
	Enabled    bool   `yaml:"enabled"`
	Interval   int    `yaml:"interval"`
	ListenPort int    `yaml:"listen_port"`
	ListenIP   string `yaml:"listen_ip"`
}

// DeviceConfig 设备配置
type DeviceConfig struct {
	DefaultUsername   string `yaml:"default_username"`
	DefaultPassword   string `yaml:"default_password"`
	DefaultPort       int    `yaml:"default_port"`
	LoginTimeout      int    `yaml:"login_timeout"`
	ReconnectInterval int    `yaml:"reconnect_interval"`
}

// KeeperConfig 保活系统配置
type KeeperConfig struct {
	Enabled          bool `yaml:"enabled"`
	CheckInterval    int  `yaml:"check_interval"`
	OfflineThreshold int  `yaml:"offline_threshold"`
}

// OSSConfig OSS配置
type OSSConfig struct {
	Enabled         bool   `yaml:"enabled"`
	Endpoint        string `yaml:"endpoint"`
	AccessKeyID     string `yaml:"access_key_id"`
	AccessKeySecret string `yaml:"access_key_secret"`
	BucketName      string `yaml:"bucket_name"`
	Region          string `yaml:"region"`
}

// DatabaseConfig 数据库配置
type DatabaseConfig struct {
	Path string `yaml:"path"`
}

// LogConfig 日志配置
type LogConfig struct {
	Level      string `yaml:"level"`
	File       string `yaml:"file"`
	MaxSize    int    `yaml:"max_size"`
	MaxBackups int    `yaml:"max_backups"`
	MaxAge     int    `yaml:"max_age"`
}

// Load 加载配置文件
func Load(path string) (*Config, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("读取配置文件失败: %w", err)
	}

	var config Config
	if err := yaml.Unmarshal(data, &config); err != nil {
		return nil, fmt.Errorf("解析配置文件失败: %w", err)
	}

	return &config, nil
}
