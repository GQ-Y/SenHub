package com.digital.video.gateway.service;

import com.digital.video.gateway.database.Database;
import com.digital.video.gateway.database.RadarDefenseZone;
import com.digital.video.gateway.database.RadarDefenseZoneDAO;
import com.digital.video.gateway.driver.livox.model.DefenseZone;
import com.digital.video.gateway.driver.livox.model.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 防区管理服务
 */
public class DefenseZoneService {
    private static final Logger logger = LoggerFactory.getLogger(DefenseZoneService.class);

    private final Database database;
    private final Connection connection;
    private final RadarDefenseZoneDAO zoneDAO;

    public DefenseZoneService(Database database) {
        this.database = database;
        this.connection = database.getConnection();
        this.zoneDAO = new RadarDefenseZoneDAO(connection);
    }

    /**
     * 创建防区
     */
    public String createZone(DefenseZone zone) {
        if (zone.getZoneId() == null) {
            zone.setZoneId("zone_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8));
        }

        RadarDefenseZone dbZone = convertToDbZone(zone);
        if (zoneDAO.save(dbZone)) {
            logger.info("防区创建成功: zoneId={}", zone.getZoneId());
            return zone.getZoneId();
        }
        return null;
    }

    /**
     * 更新防区
     */
    public boolean updateZone(String zoneId, DefenseZone zone) {
        zone.setZoneId(zoneId);
        RadarDefenseZone dbZone = convertToDbZone(zone);
        return zoneDAO.update(dbZone);
    }

    /**
     * 删除防区
     */
    public boolean deleteZone(String zoneId) {
        return zoneDAO.delete(zoneId);
    }

    /**
     * 获取防区列表
     */
    public List<DefenseZone> getZonesByDeviceId(String deviceId) {
        List<RadarDefenseZone> dbZones = zoneDAO.getByDeviceId(deviceId);
        return dbZones.stream().map(this::convertFromDbZone).collect(Collectors.toList());
    }

    /**
     * 获取防区详情
     */
    public DefenseZone getZone(String zoneId) {
        RadarDefenseZone dbZone = zoneDAO.getByZoneId(zoneId);
        return dbZone != null ? convertFromDbZone(dbZone) : null;
    }

    /**
     * 计算防区边界点云（用于前端渲染）
     */
    public List<Point> getZoneBoundary(String zoneId, List<Point> backgroundPoints) {
        DefenseZone zone = getZone(zoneId);
        if (zone == null) {
            return new ArrayList<>();
        }

        List<Point> boundaryPoints = new ArrayList<>();

        if (DefenseZone.ZONE_TYPE_SHRINK.equals(zone.getZoneType())) {
            // 缩小距离方式：计算缩小后的边界
            float shrinkDistance = zone.getShrinkDistanceCm() != null ? zone.getShrinkDistanceCm() / 100.0f : 0;
            for (Point point : backgroundPoints) {
                float distance = point.distance();
                if (distance > shrinkDistance) {
                    float scale = (distance - shrinkDistance) / distance;
                    Point shrunk = new Point(point.x * scale, point.y * scale, point.z * scale);
                    boundaryPoints.add(shrunk);
                }
            }
        } else if (DefenseZone.ZONE_TYPE_BOUNDING_BOX.equals(zone.getZoneType())) {
            // 边界框方式：返回边界框内的点
            for (Point point : backgroundPoints) {
                if (zone.isPointInZone(point)) {
                    boundaryPoints.add(point);
                }
            }
        }

        return boundaryPoints;
    }

    /**
     * 判断点是否在防区内
     */
    public boolean isPointInZone(Point point, DefenseZone zone) {
        return zone.isPointInZone(point);
    }

    /**
     * 转换DefenseZone到RadarDefenseZone
     */
    private RadarDefenseZone convertToDbZone(DefenseZone zone) {
        RadarDefenseZone dbZone = new RadarDefenseZone();
        dbZone.setZoneId(zone.getZoneId());
        dbZone.setDeviceId(zone.getDeviceId());
        dbZone.setAssemblyId(zone.getAssemblyId());
        dbZone.setBackgroundId(zone.getBackgroundId());
        dbZone.setZoneType(zone.getZoneType());
        dbZone.setShrinkDistanceCm(zone.getShrinkDistanceCm());
        dbZone.setMinX(zone.getMinX());
        dbZone.setMaxX(zone.getMaxX());
        dbZone.setMinY(zone.getMinY());
        dbZone.setMaxY(zone.getMaxY());
        dbZone.setMinZ(zone.getMinZ());
        dbZone.setMaxZ(zone.getMaxZ());
        dbZone.setCameraDeviceId(zone.getCameraDeviceId());
        dbZone.setCameraChannel(zone.getCameraChannel());
        dbZone.setCoordinateTransform(zone.getCoordinateTransformJson());
        dbZone.setEnabled(zone.getEnabled() != null && zone.getEnabled());
        dbZone.setName(zone.getName());
        dbZone.setDescription(zone.getDescription());
        return dbZone;
    }

    /**
     * 转换RadarDefenseZone到DefenseZone
     */
    private DefenseZone convertFromDbZone(RadarDefenseZone dbZone) {
        DefenseZone zone = new DefenseZone();
        zone.setZoneId(dbZone.getZoneId());
        zone.setDeviceId(dbZone.getDeviceId());
        zone.setAssemblyId(dbZone.getAssemblyId());
        zone.setBackgroundId(dbZone.getBackgroundId());
        zone.setZoneType(dbZone.getZoneType());
        zone.setShrinkDistanceCm(dbZone.getShrinkDistanceCm());
        zone.setMinX(dbZone.getMinX());
        zone.setMaxX(dbZone.getMaxX());
        zone.setMinY(dbZone.getMinY());
        zone.setMaxY(dbZone.getMaxY());
        zone.setMinZ(dbZone.getMinZ());
        zone.setMaxZ(dbZone.getMaxZ());
        zone.setCameraDeviceId(dbZone.getCameraDeviceId());
        zone.setCameraChannel(dbZone.getCameraChannel());
        zone.setCoordinateTransformJson(dbZone.getCoordinateTransform());
        zone.setEnabled(dbZone.isEnabled());
        zone.setName(dbZone.getName());
        zone.setDescription(dbZone.getDescription());
        return zone;
    }
}
