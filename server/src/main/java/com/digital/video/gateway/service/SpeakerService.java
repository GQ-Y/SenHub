package com.digital.video.gateway.service;

import com.digital.video.gateway.database.Database;
import com.digital.video.gateway.database.Speaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 音柱设备服务
 */
public class SpeakerService {
    private static final Logger logger = LoggerFactory.getLogger(SpeakerService.class);
    private final Database database;

    public SpeakerService(Database database) {
        this.database = database;
    }

    /**
     * 获取音柱列表
     */
    public List<Speaker> getSpeakers() {
        List<Speaker> speakers = new ArrayList<>();
        String sql = "SELECT * FROM speakers ORDER BY created_at DESC";
        Connection conn = database.getConnection(); try (
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                speakers.add(Speaker.fromResultSet(rs));
            }
        } catch (SQLException e) {
            logger.error("获取音柱列表失败", e);
        }
        return speakers;
    }

    /**
     * 获取音柱详情
     */
    public Speaker getSpeaker(String deviceId) {
        String sql = "SELECT * FROM speakers WHERE device_id = ?";
        Connection conn = database.getConnection(); try (
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, deviceId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Speaker.fromResultSet(rs);
            }
        } catch (SQLException e) {
            logger.error("获取音柱详情失败: {}", deviceId, e);
        }
        return null;
    }

    /**
     * 创建音柱设备
     */
    public Speaker createSpeaker(Speaker speaker) {
        String sql = "INSERT INTO speakers (device_id, name, api_endpoint, api_type, api_config, status) VALUES (?, ?, ?, ?, ?, ?)";
        Connection conn = database.getConnection(); try (
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, speaker.getDeviceId());
            pstmt.setString(2, speaker.getName());
            pstmt.setString(3, speaker.getApiEndpoint());
            pstmt.setString(4, speaker.getApiType() != null ? speaker.getApiType() : "http");
            pstmt.setString(5, speaker.getApiConfig());
            pstmt.setString(6, speaker.getStatus() != null ? speaker.getStatus() : "offline");
            pstmt.executeUpdate();
            return getSpeaker(speaker.getDeviceId());
        } catch (SQLException e) {
            logger.error("创建音柱设备失败", e);
            return null;
        }
    }

    /**
     * 更新音柱设备
     */
    public Speaker updateSpeaker(String deviceId, Speaker speaker) {
        String sql = "UPDATE speakers SET name = ?, api_endpoint = ?, api_type = ?, api_config = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE device_id = ?";
        Connection conn = database.getConnection(); try (
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, speaker.getName());
            pstmt.setString(2, speaker.getApiEndpoint());
            pstmt.setString(3, speaker.getApiType());
            pstmt.setString(4, speaker.getApiConfig());
            pstmt.setString(5, speaker.getStatus());
            pstmt.setString(6, deviceId);
            pstmt.executeUpdate();
            return getSpeaker(deviceId);
        } catch (SQLException e) {
            logger.error("更新音柱设备失败: {}", deviceId, e);
            return null;
        }
    }

    /**
     * 删除音柱设备
     */
    public boolean deleteSpeaker(String deviceId) {
        String sql = "DELETE FROM speakers WHERE device_id = ?";
        Connection conn = database.getConnection(); try (
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, deviceId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("删除音柱设备失败: {}", deviceId, e);
            return false;
        }
    }

    /**
     * 播放语音（暂时作为空缺陷实现）
     */
    public boolean playVoice(String deviceId, String text) {
        Speaker speaker = getSpeaker(deviceId);
        if (speaker == null) {
            logger.warn("音柱设备不存在: {}", deviceId);
            return false;
        }

        // TODO: 实现音柱API调用
        // 根据apiType调用不同的API（http, mqtt, tcp）
        logger.info("音柱播放语音（暂未实现）: deviceId={}, text={}", deviceId, text);
        return false;
    }
}
