package com.digital.video.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * 雷达连接测试服务
 * 专门用于测试与览沃 Mid-360 雷达的通讯连通性
 * 优先使用 SDK 检测到的设备信息，握手检测作为备用
 */
public class RadarTestService {
    private static final Logger logger = LoggerFactory.getLogger(RadarTestService.class);
    private static final int COMMAND_PORT = 56100; // 雷达命令端口
    private static final int DISCOVERY_PORT = 55000; // 宿主机发现端口
    
    private RadarService radarService; // 用于查询 SDK 检测到的设备信息
    
    /**
     * 默认构造函数（无 SDK 查询能力）
     */
    public RadarTestService() {
        this.radarService = null;
    }
    
    /**
     * 带 RadarService 的构造函数（支持 SDK 查询）
     */
    public RadarTestService(RadarService radarService) {
        this.radarService = radarService;
    }
    
    /**
     * 设置 RadarService（用于延迟注入）
     */
    public void setRadarService(RadarService radarService) {
        this.radarService = radarService;
    }

    /**
     * 测试雷达连通性
     * 优先使用 SDK 检测到的设备信息，如果 SDK 已经检测到该设备则直接返回成功
     * 
     * @param lidarIp 雷达 IP
     * @return 测试结果描述
     */
    public RadarDetectionResult testConnection(String lidarIp) {
        logger.info("开始雷达连接测试: {}", lidarIp);
        StringBuilder result = new StringBuilder();
        RadarDetectionResult detectionResult = new RadarDetectionResult();
        detectionResult.setIp(lidarIp);

        // 0. 优先检查 SDK 是否已经检测到该设备
        String sdkSerial = tryGetSerialFromSDK(lidarIp);
        if (sdkSerial != null && !sdkSerial.isEmpty()) {
            result.append("0. SDK 设备检测: 成功\n");
            result.append("   - 序列号: ").append(sdkSerial).append("\n");
            result.append("   - 说明: SDK 已正常连接该雷达，点云数据接收正常\n");
            detectionResult.setReachable(true);
            detectionResult.setRadarSerial(sdkSerial);
            detectionResult.setMessage(result.toString());
            logger.info("SDK 已检测到雷达: ip={}, serial={}", lidarIp, sdkSerial);
            return detectionResult;
        }
        result.append("0. SDK 设备检测: 未检测到 (SDK 可能尚未连接该设备)\n");

        // 1. 网络层连通性 (ICMP)
        try {
            InetAddress address = InetAddress.getByName(lidarIp);
            if (address.isReachable(2000)) {
                result.append("1. 网络连通性 (Ping): 正常\n");
                detectionResult.setReachable(true);
            } else {
                result.append("1. 网络连通性 (Ping): 超时 (请检查 IP 或物理链路)\n");
                detectionResult.setReachable(false);
                detectionResult.setMessage(result.toString());
                return detectionResult;
            }
        } catch (Exception e) {
            result.append("1. 网络连通性 (Ping): 异常 (").append(e.getMessage()).append(")\n");
            detectionResult.setReachable(false);
            detectionResult.setMessage(result.toString());
            return detectionResult;
        }

        // 2. 尝试发现广播 (UDP 55000)
        // 注意：这需要在后台监听，通常雷达每秒广播一次
        result.append("2. 广播发现测试: 正在监听 55000 端口...\n");
        boolean discovered = listenForDiscovery(lidarIp, 5000);
        if (discovered) {
            result.append("   - 结果: 成功接收到设备广播信息数据包\n");
        } else {
            result.append("   - 结果: 未能在 5s 内探测到设备广播 (可能是防火墙阻拦或端口被占用)\n");
        }

        // 3. 尝试发送握手包 (UDP 56100) - 作为备用检测方式
        result.append("3. 协议握手测试 (Port ").append(COMMAND_PORT).append("): 正在发送探测包...\n");
        HandshakeResult handshakeResult = tryHandshake(lidarIp);
        if (handshakeResult.isSuccess()) {
            result.append("   - 结果: 成功建立协议握手，雷达响应正常\n");
            if (handshakeResult.getRadarSerial() != null) {
                // 只有当 SDK 未提供序列号时才使用握手获取的序列号
                if (detectionResult.getRadarSerial() == null) {
                    detectionResult.setRadarSerial(handshakeResult.getRadarSerial());
                }
                result.append("   - 序列号: ").append(handshakeResult.getRadarSerial()).append("\n");
            }
        } else {
            result.append("   - 结果: 握手未响应 (这不影响实际连接，SDK 可能通过其他方式工作)\n");
        }

        // 4. 再次尝试从 SDK 获取序列号（可能在检测过程中 SDK 完成了连接）
        if (detectionResult.getRadarSerial() == null) {
            sdkSerial = tryGetSerialFromSDK(lidarIp);
            if (sdkSerial != null && !sdkSerial.isEmpty()) {
                detectionResult.setRadarSerial(sdkSerial);
                result.append("4. SDK 延迟检测: 成功获取序列号 ").append(sdkSerial).append("\n");
            }
        }

        detectionResult.setReachable(true);
        detectionResult.setMessage(result.toString());
        logger.info("雷达连接测试完成: {}\n{}", lidarIp, result.toString());
        return detectionResult;
    }
    
    /**
     * 尝试从 SDK 获取设备序列号
     * @param ip 设备 IP
     * @return 序列号，如果未获取到返回 null
     */
    private String tryGetSerialFromSDK(String ip) {
        if (radarService == null) {
            logger.debug("RadarService 未注入，无法从 SDK 查询设备信息");
            return null;
        }
        try {
            return radarService.getDeviceSerialByIp(ip);
        } catch (Exception e) {
            logger.debug("从 SDK 获取设备序列号失败: {}", e.getMessage());
            return null;
        }
    }

    private boolean listenForDiscovery(String targetIp, int timeoutMs) {
        try (DatagramSocket socket = new DatagramSocket(DISCOVERY_PORT)) {
            socket.setSoTimeout(timeoutMs);
            byte[] buf = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < timeoutMs) {
                socket.receive(packet);
                String senderIp = packet.getAddress().getHostAddress();
                if (targetIp.equals(senderIp)) {
                    return true;
                }
            }
        } catch (Exception e) {
            logger.debug("广播监听异常: {}", e.getMessage());
        }
        return false;
    }

    private HandshakeResult tryHandshake(String lidarIp) {
        HandshakeResult result = new HandshakeResult();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(3000);
            InetAddress address = InetAddress.getByName(lidarIp);

            // 构造一个简单的 Livox SDK 2.0 GetDeviceInfo 请求包
            // AA 01 0F 00 00 00 00 00 00 00 01 00 00 ... (简化版，包含协议头)
            byte[] getDeviceInfoCmd = new byte[] {
                    (byte) 0xAA, 0x01, 0x0F, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x01, 0x00, 0x00, (byte) 0xAD, (byte) 0xF0 // 简单固定包
            };

            DatagramPacket sendPacket = new DatagramPacket(getDeviceInfoCmd, getDeviceInfoCmd.length, address,
                    COMMAND_PORT);
            socket.send(sendPacket);

            // 尝试接收响应
            byte[] buf = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(receivePacket);
                String senderIp = receivePacket.getAddress().getHostAddress();
                if (lidarIp.equals(senderIp)) {
                    logger.info("接收到来自雷达 {} 的握手响应", senderIp);
                    result.setSuccess(true);
                    // 尝试从响应中解析序列号（以可打印ASCII片段作为候选）
                    String serial = extractAsciiSerial(receivePacket.getData(), receivePacket.getLength());
                    if (serial != null) {
                        result.setRadarSerial(serial);
                    }
                    return result;
                }
            } catch (java.net.SocketTimeoutException e) {
                logger.warn("雷达握手响应超时: {}", lidarIp);
            }
            return result;
        } catch (Exception e) {
            logger.error("雷达握手异常", e);
            return result;
        }
    }

    /**
     * 简单从响应中提取可打印ASCII字符串作为序列号候选
     */
    private String extractAsciiSerial(byte[] data, int length) {
        if (data == null || length <= 0) {
            return null;
        }
        StringBuilder candidate = new StringBuilder();
        String best = null;
        for (int i = 0; i < length; i++) {
            byte b = data[i];
            if (b >= 32 && b <= 126) {
                candidate.append((char) b);
            } else {
                if (candidate.length() >= 6) {
                    best = candidate.toString();
                    break;
                }
                candidate.setLength(0);
            }
        }
        if (best == null && candidate.length() >= 6) {
            best = candidate.toString();
        }
        return best != null ? best.trim() : null;
    }

    /**
     * 检测结果封装
     */
    public static class RadarDetectionResult {
        private boolean reachable;
        private String radarSerial;
        private String message;
        private String ip;

        public boolean isReachable() {
            return reachable;
        }

        public void setReachable(boolean reachable) {
            this.reachable = reachable;
        }

        public String getRadarSerial() {
            return radarSerial;
        }

        public void setRadarSerial(String radarSerial) {
            this.radarSerial = radarSerial;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }
    }

    /**
     * 握手结果封装
     */
    private static class HandshakeResult {
        private boolean success;
        private String radarSerial;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getRadarSerial() {
            return radarSerial;
        }

        public void setRadarSerial(String radarSerial) {
            this.radarSerial = radarSerial;
        }

    }
}
