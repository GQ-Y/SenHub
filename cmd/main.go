package main

import (
	"log"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"

	"hikvision-nvr-control/internal/command"
	"hikvision-nvr-control/internal/config"
	"hikvision-nvr-control/internal/device"
	"hikvision-nvr-control/internal/hikvision"
	"hikvision-nvr-control/internal/keeper"
	"hikvision-nvr-control/internal/mqtt"
	"hikvision-nvr-control/internal/oss"
	"hikvision-nvr-control/internal/scanner"

	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
)

func main() {
	// 加载配置
	cfg, err := config.Load("config.yaml")
	if err != nil {
		log.Fatalf("加载配置失败: %v", err)
	}

	// 创建必要的目录
	if err := os.MkdirAll(filepath.Dir(cfg.Database.Path), 0755); err != nil {
		log.Fatalf("创建数据库目录失败: %v", err)
	}
	if err := os.MkdirAll(filepath.Dir(cfg.Log.File), 0755); err != nil {
		log.Fatalf("创建日志目录失败: %v", err)
	}

	// 初始化数据库
	db, err := gorm.Open(sqlite.Open(cfg.Database.Path), &gorm.Config{})
	if err != nil {
		log.Fatalf("连接数据库失败: %v", err)
	}

	// 自动迁移数据库
	if err := db.AutoMigrate(&device.Device{}); err != nil {
		log.Fatalf("数据库迁移失败: %v", err)
	}

	// 初始化SDK
	sdk := hikvision.NewSDK()
	if err := sdk.Init(); err != nil {
		log.Fatalf("SDK初始化失败: %v", err)
	}
	defer sdk.Cleanup()

	// 设置日志
	sdk.SetLogToFile(3, "./sdkLog")

	// 创建设备仓储和管理器
	deviceRepo := device.NewDeviceRepository(db)
	deviceManager := device.NewManager(cfg, sdk, deviceRepo)

	// 初始化MQTT客户端
	mqttClient := mqtt.NewClient(cfg)
	if err := mqttClient.Connect(); err != nil {
		log.Fatalf("MQTT连接失败: %v", err)
	}
	defer mqttClient.Disconnect()

	// 初始化OSS客户端
	ossClient := oss.NewClient(cfg)

	// 初始化命令处理器
	cmdHandler := command.NewHandler(cfg, deviceManager, sdk, mqttClient, ossClient)
	mqttClient.RegisterHandler(cfg.MQTT.CommandTopic, cmdHandler.HandleCommand)

	// 初始化扫描器
	scanner := scanner.NewScanner(cfg, sdk, deviceManager)
	if err := scanner.Start(); err != nil {
		log.Fatalf("启动扫描器失败: %v", err)
	}
	defer scanner.Stop()

	// 初始化保活系统
	keeper := keeper.NewKeeper(cfg, deviceManager, mqttClient)
	if err := keeper.Start(); err != nil {
		log.Fatalf("启动保活系统失败: %v", err)
	}
	defer keeper.Stop()

	log.Println("海康威视NVR控制服务已启动")

	// 等待中断信号
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, os.Interrupt, syscall.SIGTERM)
	<-sigChan

	log.Println("正在关闭服务...")
}
