package mqtt

import (
	"encoding/json"
	"fmt"
	"log"
	"time"

	mqtt "github.com/eclipse/paho.mqtt.golang"
	"hikvision-nvr-control/internal/config"
)

// Client MQTT客户端
type Client struct {
	config   *config.Config
	client   mqtt.Client
	handlers map[string]MessageHandler
}

// MessageHandler 消息处理函数类型
type MessageHandler func(topic string, payload []byte) error

// NewClient 创建MQTT客户端
func NewClient(cfg *config.Config) *Client {
	return &Client{
		config:   cfg,
		handlers: make(map[string]MessageHandler),
	}
}

// Connect 连接MQTT服务器
func (c *Client) Connect() error {
	opts := mqtt.NewClientOptions()
	opts.AddBroker(c.config.MQTT.Broker)
	opts.SetClientID(c.config.MQTT.ClientID)
	
	if c.config.MQTT.Username != "" {
		opts.SetUsername(c.config.MQTT.Username)
	}
	if c.config.MQTT.Password != "" {
		opts.SetPassword(c.config.MQTT.Password)
	}
	
	opts.SetKeepAlive(time.Duration(c.config.MQTT.KeepAlive) * time.Second)
	opts.SetAutoReconnect(true)
	opts.SetConnectRetry(true)
	opts.SetConnectRetryInterval(5 * time.Second)
	
	opts.SetOnConnectHandler(func(client mqtt.Client) {
		log.Println("MQTT客户端已连接")
		
		// 订阅命令主题
		token := client.Subscribe(c.config.MQTT.CommandTopic, c.config.MQTT.QoS, c.messageHandler)
		if token.Wait() && token.Error() != nil {
			log.Printf("订阅命令主题失败: %v", token.Error())
		} else {
			log.Printf("已订阅命令主题: %s", c.config.MQTT.CommandTopic)
		}
	})
	
	opts.SetConnectionLostHandler(func(client mqtt.Client, err error) {
		log.Printf("MQTT连接丢失: %v", err)
	})
	
	c.client = mqtt.NewClient(opts)
	
	token := c.client.Connect()
	if token.Wait() && token.Error() != nil {
		return fmt.Errorf("MQTT连接失败: %w", token.Error())
	}
	
	return nil
}

// Disconnect 断开连接
func (c *Client) Disconnect() {
	if c.client != nil && c.client.IsConnected() {
		c.client.Disconnect(250)
		log.Println("MQTT客户端已断开")
	}
}

// messageHandler MQTT消息处理
func (c *Client) messageHandler(client mqtt.Client, msg mqtt.Message) {
	topic := msg.Topic()
	payload := msg.Payload()
	
	log.Printf("收到MQTT消息: topic=%s, payload=%s", topic, string(payload))
	
	// 查找对应的处理器
	if handler, exists := c.handlers[topic]; exists {
		if err := handler(topic, payload); err != nil {
			log.Printf("处理消息失败: %v", err)
		}
	} else {
		log.Printf("未找到消息处理器: %s", topic)
	}
}

// RegisterHandler 注册消息处理器
func (c *Client) RegisterHandler(topic string, handler MessageHandler) {
	c.handlers[topic] = handler
}

// Publish 发布消息
func (c *Client) Publish(topic string, qos byte, retained bool, payload interface{}) error {
	if !c.client.IsConnected() {
		return fmt.Errorf("MQTT客户端未连接")
	}
	
	var data []byte
	var err error
	
	switch v := payload.(type) {
	case []byte:
		data = v
	case string:
		data = []byte(v)
	default:
		data, err = json.Marshal(payload)
		if err != nil {
			return fmt.Errorf("序列化消息失败: %w", err)
		}
	}
	
	token := c.client.Publish(topic, qos, retained, data)
	if token.Wait() && token.Error() != nil {
		return fmt.Errorf("发布消息失败: %w", token.Error())
	}
	
	return nil
}

// PublishStatus 发布状态消息
func (c *Client) PublishStatus(status interface{}) error {
	return c.Publish(c.config.MQTT.StatusTopic, c.config.MQTT.QoS, false, status)
}

// PublishResponse 发布响应消息
func (c *Client) PublishResponse(response interface{}) error {
	return c.Publish(c.config.MQTT.ResponseTopic, c.config.MQTT.QoS, false, response)
}

// IsConnected 检查是否已连接
func (c *Client) IsConnected() bool {
	return c.client != nil && c.client.IsConnected()
}
