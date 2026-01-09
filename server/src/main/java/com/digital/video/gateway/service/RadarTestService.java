package com.digital.video.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * 雷达连接测试服务
 * 专门用于测试与览沃 Mid-360 雷达的通讯连通性
 */
public class RadarTestService {
    private static final Logger logger = LoggerFactory.getLogger(RadarTestService.class);
    private static final int COMMAND_PORT = 56100; // 雷达命令端口
    private static final int DISCOVERY_PORT = 55000; // 宿主机发现端口

    /**
     * 测试雷达连通性
     * 
     * @param lidarIp 雷达 IP
     * @return 测试结果描述
     */
    public String testConnection(String lidarIp) {
        logger.info("开始雷达连接测试: {}", lidarIp);
        StringBuilder result = new StringBuilder();

        // 1. 网络层连通性 (ICMP)
        try {
            InetAddress address = InetAddress.getByName(lidarIp);
            if (address.isReachable(2000)) {
                result.append("1. 网络连通性 (Ping): 正常\n");
            } else {
                result.append("1. 网络连通性 (Ping): 超时 (请检查 IP 或物理链路)\n");
                return result.toString();
            }
        } catch (Exception e) {
            result.append("1. 网络连通性 (Ping): 异常 (").append(e.getMessage()).append(")\n");
            return result.toString();
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

        // 3. 尝试发送握手包 (UDP 56100)
        result.append("3. 协议握手测试 (Port ").append(COMMAND_PORT).append("): 正在发送探测包...\n");
        boolean handshakeOk = tryHandshake(lidarIp);
        if (handshakeOk) {
            result.append("   - 结果: 成功建立协议握手，雷达响应正常\n");
        } else {
            result.append("   - 结果: 握手失败或雷达未响应命令端口\n");
        }

        logger.info("雷达连接测试完成: {}\n{}", lidarIp, result.toString());
        return result.toString();
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

    private boolean tryHandshake(String lidarIp) {
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
                    return true;
                }
            } catch (java.net.SocketTimeoutException e) {
                logger.warn("雷达握手响应超时: {}", lidarIp);
            }
            return false;
        } catch (Exception e) {
            logger.error("雷达握手异常", e);
            return false;
        }
    }
}
