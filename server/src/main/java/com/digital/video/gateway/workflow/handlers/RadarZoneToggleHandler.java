package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.database.Database;
import com.digital.video.gateway.driver.livox.model.DefenseZone;
import com.digital.video.gateway.service.DefenseZoneService;
import com.digital.video.gateway.service.RadarService;
import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowNodeDefinition;
import com.digital.video.gateway.workflow.FlowNodeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 雷达防区启用/关闭节点：布防/撤防、定时或事件联动。
 * config: radarDeviceId（可选，可从 zone 解析）、zoneId（必填）、enabled（boolean）
 */
public class RadarZoneToggleHandler implements FlowNodeHandler {
    private static final Logger logger = LoggerFactory.getLogger(RadarZoneToggleHandler.class);
    private final DefenseZoneService defenseZoneService;
    private final RadarService radarService;

    public RadarZoneToggleHandler(Database database, RadarService radarService) {
        this.defenseZoneService = new DefenseZoneService(database);
        this.radarService = radarService;
    }

    @Override
    public boolean execute(FlowNodeDefinition node, FlowContext context) {
        Map<String, Object> cfg = node.getConfig();
        if (cfg == null || cfg.get("zoneId") == null) {
            logger.warn("radar_zone_toggle 节点未配置 zoneId，跳过");
            return false;
        }
        String zoneId = cfg.get("zoneId").toString().trim();
        if (zoneId.isEmpty()) {
            logger.warn("radar_zone_toggle zoneId 为空，跳过");
            return false;
        }

        Boolean enabled = true;
        if (cfg.get("enabled") != null) {
            if (cfg.get("enabled") instanceof Boolean) {
                enabled = (Boolean) cfg.get("enabled");
            } else if (cfg.get("enabled") instanceof String) {
                enabled = Boolean.parseBoolean(((String) cfg.get("enabled")).trim());
            }
        }

        DefenseZone zone = defenseZoneService.getZone(zoneId);
        if (zone == null) {
            logger.warn("radar_zone_toggle 防区不存在: zoneId={}", zoneId);
            return false;
        }

        String radarDeviceId = null;
        if (cfg.get("radarDeviceId") instanceof String) {
            String raw = (String) cfg.get("radarDeviceId");
            radarDeviceId = HandlerUtils.renderTemplate(raw, context, context.getVariables() != null ? new java.util.HashMap<>(context.getVariables()) : null);
        }
        if (radarDeviceId == null || radarDeviceId.isBlank()) {
            radarDeviceId = zone.getDeviceId();
        }

        zone.setEnabled(enabled);
        if (!defenseZoneService.updateZone(zoneId, zone)) {
            logger.warn("radar_zone_toggle 更新防区失败: zoneId={}", zoneId);
            return false;
        }
        if (radarService != null && radarDeviceId != null && !radarDeviceId.isBlank()) {
            radarService.reloadDeviceDetectionContext(radarDeviceId);
        }
        logger.info("radar_zone_toggle: zoneId={}, enabled={}, radarDeviceId={}", zoneId, enabled, radarDeviceId);
        return true;
    }
}
