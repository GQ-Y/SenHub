package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.driver.livox.model.Point;
import com.digital.video.gateway.service.RadarService;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 雷达WebSocket端点
 * 使用Jetty WebSocket实现实时点云数据推送
 */
@WebSocket
public class RadarWebSocketEndpoint {
    private static final Logger logger = LoggerFactory.getLogger(RadarWebSocketEndpoint.class);
    
    private static final Map<String, RadarWebSocketHandler> handlers = new ConcurrentHashMap<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 注册WebSocket处理器
     */
    public static void registerHandler(String deviceId, RadarWebSocketHandler handler) {
        handlers.put(deviceId, handler);
        logger.info("注册WebSocket处理器: deviceId={}", deviceId);
    }
    
    // 存储session到deviceId的映射
    private static final Map<Session, String> sessionToDeviceId = new ConcurrentHashMap<>();
    
    /**
     * WebSocket连接建立
     */
    @OnWebSocketConnect
    public void onConnect(Session session) {
        try {
            // Spark WebSocket不支持路径参数，从查询参数获取deviceId
            String deviceId = null;
            String queryString = session.getUpgradeRequest().getQueryString();
            java.net.URI requestURI = session.getUpgradeRequest().getRequestURI();
            
            // 详细日志输出
            logger.info("WebSocket连接请求详情: 完整URI={}, QueryString={}, RequestURI={}", 
                    session.getUpgradeRequest().getRequestURI(), 
                    queryString,
                    requestURI);
            
            // 方法1: 优先从QueryString获取（这是最可靠的方法）
            if (queryString != null && !queryString.isEmpty()) {
                String[] pairs = queryString.split("&");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=", 2);
                    if (keyValue.length == 2 && "deviceId".equals(keyValue[0])) {
                        try {
                            deviceId = URLDecoder.decode(keyValue[1], "UTF-8");
                            logger.info("从QueryString解析deviceId成功: {}", deviceId);
                            break;
                        } catch (UnsupportedEncodingException e) {
                            logger.warn("解码deviceId失败", e);
                        }
                    }
                }
            }
            
            // 方法2: 如果QueryString没有，尝试从URI的查询部分获取
            if ((deviceId == null || deviceId.trim().isEmpty()) && requestURI != null) {
                String query = requestURI.getQuery();
                if (query != null && !query.isEmpty()) {
                    String[] pairs = query.split("&");
                    for (String pair : pairs) {
                        String[] keyValue = pair.split("=", 2);
                        if (keyValue.length == 2 && "deviceId".equals(keyValue[0])) {
                            try {
                                deviceId = URLDecoder.decode(keyValue[1], "UTF-8");
                                logger.info("从URI查询字符串解析deviceId成功: {}", deviceId);
                                break;
                            } catch (UnsupportedEncodingException e) {
                                logger.warn("解码deviceId失败", e);
                            }
                        }
                    }
                }
            }
            
            // 方法3: 如果还没有，尝试从ParameterMap获取
            if ((deviceId == null || deviceId.trim().isEmpty()) && session.getUpgradeRequest().getParameterMap() != null) {
                Map<String, List<String>> params = session.getUpgradeRequest().getParameterMap();
                logger.info("ParameterMap内容: {}", params);
                if (params.containsKey("deviceId")) {
                    List<String> deviceIdList = params.get("deviceId");
                    if (deviceIdList != null && !deviceIdList.isEmpty()) {
                        deviceId = deviceIdList.get(0);
                        logger.info("从ParameterMap解析deviceId: {}", deviceId);
                    }
                }
            }
            
            logger.info("最终解析的deviceId: {}", deviceId);
            
            if (deviceId == null || deviceId.trim().isEmpty()) {
                logger.warn("WebSocket连接缺少deviceId查询参数: URI={}", requestURI);
                session.close(1008, "Invalid request: missing deviceId parameter");
                return;
            }
            
            logger.info("WebSocket连接建立: deviceId={}, sessionId={}", deviceId, session.hashCode());
            
            // 获取或创建处理器
            RadarWebSocketHandler handler = handlers.get(deviceId);
            if (handler == null) {
                // 尝试使用默认处理器
                handler = handlers.get("default");
                if (handler == null) {
                    logger.warn("未找到deviceId对应的处理器: {}", deviceId);
                    session.close(1008, "Device handler not found");
                    return;
                }
            }
            
            // 存储映射
            sessionToDeviceId.put(session, deviceId);
            
            // 添加连接到处理器
            handler.addConnection(deviceId, session);
            
            // 发送连接成功消息
            Map<String, Object> welcome = new HashMap<>();
            welcome.put("type", "status");
            welcome.put("connected", true);
            welcome.put("deviceId", deviceId);
            welcome.put("message", "WebSocket连接成功");
            session.getRemote().sendString(objectMapper.writeValueAsString(welcome));
            
        } catch (Exception e) {
            logger.error("WebSocket连接处理失败", e);
            try {
                session.close(1011, "Internal error");
            } catch (Exception ex) {
                logger.error("关闭WebSocket连接失败", ex);
            }
        }
    }
    
    /**
     * WebSocket消息接收
     */
    @OnWebSocketMessage
    public void onMessage(Session session, String message) {
        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            String type = (String) data.get("type");
            
            if ("subscribe".equals(type)) {
                // 订阅消息，已通过连接建立时处理
                logger.debug("收到订阅消息: {}", message);
            } else {
                logger.debug("收到WebSocket消息: {}", message);
            }
        } catch (Exception e) {
            logger.error("处理WebSocket消息失败", e);
        }
    }
    
    /**
     * WebSocket连接关闭
     */
    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason) {
        String deviceId = sessionToDeviceId.remove(session);
        logger.info("WebSocket连接关闭: deviceId={}, sessionId={}, statusCode={}, reason={}", 
                deviceId, session.hashCode(), statusCode, reason);
        
        // 从所有处理器中移除连接
        for (RadarWebSocketHandler handler : handlers.values()) {
            handler.removeConnection(session);
        }
    }
}
