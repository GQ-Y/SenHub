package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.service.AiAnalysisService;
import com.digital.video.gateway.service.AiGatewayClient;
import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowNodeDefinition;
import com.digital.video.gateway.workflow.FlowNodeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 警示语生成节点：根据报警事件类型和核验结果生成简洁中文警示语文本，写入 context.ai_alert_text。
 */
public class AiAlertTextHandler implements FlowNodeHandler {
    private static final Logger logger = LoggerFactory.getLogger(AiAlertTextHandler.class);

    private static final String SYSTEM_PROMPT =
            "你是一位经验丰富的现场安全员，负责通过广播系统实时提醒施工现场人员注意安全。\n" +
            "请根据给定的报警事件信息，用安全员的专业口吻生成一段中文警示语播报文本。\n\n" +
            "【角色要求】\n" +
            "- 语气坚定但关切，像一位负责任的安全员在现场喊话\n" +
            "- 称呼使用'各位工友''现场人员'等贴近一线的称谓\n" +
            "- 先指出违规现象，再给出明确的纠正要求\n\n" +
            "【场景对应规则】\n" +
            "- 未佩戴安全帽/头盔类：提醒立即佩戴安全帽，强调高空坠物风险\n" +
            "- 未穿反光衣/工装类：提醒穿好反光背心或工装，强调可视性与人车混行风险\n" +
            "- 区域入侵/越界类：警告已进入危险区域或禁区，要求立即撤离\n" +
            "- 人员聚集/密度过高类：提醒保持安全间距，注意疏散通道畅通\n" +
            "- 烟火/明火检测类：发出火灾隐患警告，要求排查火源并准备灭火器材\n" +
            "- 人员摔倒/异常行为类：提醒注意脚下安全，关注身边工友状况\n" +
            "- 车辆违停/交通类：提醒规范停放车辆，保持消防通道和作业通道畅通\n" +
            "- 攀爬/高处作业类：提醒系好安全带，做好高处作业防护\n" +
            "- 其他安全违规：根据具体事件，给出对应的安全操作规范和整改要求\n\n" +
            "【输出要求】\n" +
            "- 字数控制在30~60字之间，适合语音播报\n" +
            "- 直接输出播报文本，不要引号、序号或多余说明\n" +
            "- 结尾带一句简短的安全提醒或鼓励语，如'安全无小事，防范靠大家'等（每次可变化）";

    private static final Map<String, String> FALLBACK_TEMPLATES = new LinkedHashMap<>();
    static {
        FALLBACK_TEMPLATES.put("反光衣",
                "各位工友请注意，监控发现现场有人员未穿反光背心，人车混行区域存在安全隐患，请立即穿好反光衣。安全无小事，防范靠大家！");
        FALLBACK_TEMPLATES.put("安全帽",
                "各位工友请注意，监控发现现场有人员未佩戴安全帽，高空坠物风险不容忽视，请立即戴好安全帽。生命至上，安全第一！");
        FALLBACK_TEMPLATES.put("头盔",
                "现场人员请注意，监控发现有人员未佩戴安全头盔，请立即佩戴到位，做好个人防护。你的安全，大家的牵挂！");
        FALLBACK_TEMPLATES.put("越界",
                "现场人员请注意，监控发现有人员越界进入危险区域，请立即退回安全区域，严禁擅自进入警戒范围。遵守规定，平安回家！");
        FALLBACK_TEMPLATES.put("入侵",
                "注意，监控发现有人员进入禁止区域，请相关人员立即撤离，严禁在危险区域逗留。安全防线不可越，生命红线不能碰！");
        FALLBACK_TEMPLATES.put("周界",
                "现场人员请注意，周界防护区域检测到异常活动，请安保人员立即前往核实处置。安全防范，人人有责！");
        FALLBACK_TEMPLATES.put("徘徊",
                "现场人员请注意，监控发现有人员在敏感区域长时间徘徊，请安保人员关注并核实情况。防患于未然，安全记心间！");
        FALLBACK_TEMPLATES.put("烟",
                "紧急提醒，监控发现现场有疑似烟雾，请立即排查火源隐患，准备好灭火器材，严禁违规动火作业。消除隐患，守护平安！");
        FALLBACK_TEMPLATES.put("火",
                "紧急警告，监控发现现场有疑似明火，请立即报告并启动应急措施，做好灭火准备。火情不等人，处置要迅速！");
        FALLBACK_TEMPLATES.put("摔倒",
                "现场人员请注意，监控发现有人员摔倒，请附近工友立即前往查看并协助处理，注意脚下安全。互帮互助，安全作业！");
        FALLBACK_TEMPLATES.put("聚集",
                "现场人员请注意，监控发现区域内人员过于密集，请保持安全间距，确保疏散通道畅通。有序作业，安全施工！");
        FALLBACK_TEMPLATES.put("车辆",
                "现场人员请注意，监控发现有车辆违规停放，请车主立即挪车，保持消防通道和作业通道畅通。规范停放，保障安全！");
        FALLBACK_TEMPLATES.put("攀爬",
                "现场人员请注意，监控发现有人员违规攀爬，请立即停止并使用规范通道，做好高处作业防护。高处不胜寒，安全系身边！");
        FALLBACK_TEMPLATES.put("安全带",
                "高处作业人员请注意，监控发现有人员未系安全带，请立即系好安全带并检查挂钩点。生命只有一次，安全带必须系！");
        FALLBACK_TEMPLATES.put("工装",
                "现场人员请注意，监控发现有人员未按规定穿着作业工装，请立即整改，做好个人劳动防护。规范着装，安全保障！");
        FALLBACK_TEMPLATES.put("打电话",
                "现场人员请注意，作业区域内严禁使用手机通话，请到安全区域接听电话，避免分心引发事故。专注作业，远离危险！");
        FALLBACK_TEMPLATES.put("吸烟",
                "现场人员请注意，施工区域严禁吸烟，请立即熄灭烟头并到指定区域。防火防爆，人人有责！");
    }

    private final AiGatewayClient aiClient;
    private final AiAnalysisService aiAnalysisService;

    public AiAlertTextHandler(AiGatewayClient aiClient) {
        this.aiClient = aiClient;
        this.aiAnalysisService = null;
    }

    public AiAlertTextHandler(AiGatewayClient aiClient, AiAnalysisService aiAnalysisService) {
        this.aiClient = aiClient;
        this.aiAnalysisService = aiAnalysisService;
    }

    @Override
    public boolean execute(FlowNodeDefinition node, FlowContext context) throws Exception {
        String alarmType = context.getAlarmType() != null ? context.getAlarmType() : "报警";
        String deviceId = context.getDeviceId() != null ? context.getDeviceId() : "";
        String verifyReason = null;
        if (context.getVariables() != null && context.getVariables().get("ai_verify_reason") instanceof String) {
            verifyReason = (String) context.getVariables().get("ai_verify_reason");
        }
        String eventNameZh = null;
        if (context.getVariables() != null && context.getVariables().get("eventNameZh") instanceof String) {
            eventNameZh = (String) context.getVariables().get("eventNameZh");
        }

        String displayName = (eventNameZh != null && !eventNameZh.isEmpty()) ? eventNameZh : alarmType;

        if (aiClient == null) {
            logger.warn("AiGatewayClient 未初始化，使用回退警示语");
            String fallback = buildFallbackText(displayName, verifyReason);
            context.putVariable("ai_alert_text", fallback);
            return true;
        }

        StringBuilder userContent = new StringBuilder();
        userContent.append("【报警信息】\n");
        userContent.append("事件类型编码：").append(alarmType);
        if (eventNameZh != null && !eventNameZh.isEmpty()) {
            userContent.append("\n事件中文名称：").append(eventNameZh);
        }
        if (!deviceId.isEmpty()) {
            userContent.append("\n监控点位：").append(deviceId);
        }
        if (verifyReason != null && !verifyReason.isEmpty() && !verifyReason.contains("跳过")) {
            userContent.append("\nAI核验描述：").append(verifyReason);
        }
        userContent.append("\n\n请以安全员的口吻，针对上述事件生成一段现场广播警示语。");

        Map<String, Object> cfg = node.getConfig();
        if (cfg != null && cfg.get("prompt") instanceof String) {
            String extra = ((String) cfg.get("prompt")).trim();
            if (!extra.isEmpty()) {
                userContent.append("\n附加要求：").append(extra);
            }
        }

        String model = null;
        if (cfg != null && cfg.get("model") instanceof String) {
            model = ((String) cfg.get("model")).trim();
            if (model.isEmpty()) model = null;
        }

        List<Map<String, Object>> messages = List.of(
                Map.<String, Object>of("role", "system", "content", SYSTEM_PROMPT),
                Map.<String, Object>of("role", "user", "content", userContent.toString())
        );

        String alertText = null;
        try {
            String text = aiClient.chatCompletion(messages, model, 200);
            if (text != null && !text.isBlank()) {
                alertText = text.trim();
                logger.info("ai_alert_text AI生成: {}", alertText);
            }
        } catch (Exception e) {
            logger.warn("ai_alert_text AI调用失败，使用回退警示语: {}", e.getMessage());
        }

        if (alertText == null || alertText.isBlank()) {
            alertText = buildFallbackText(displayName, verifyReason);
            logger.info("ai_alert_text 回退: {}", alertText);
        }

        context.putVariable("ai_alert_text", alertText);

        if (aiAnalysisService != null && context.getVariables() != null) {
            Object recordId = context.getVariables().get("_ai_analysis_record_id");
            if (recordId instanceof String) {
                aiAnalysisService.updateField((String) recordId, "alertText", alertText);
            }
        }

        return true;
    }

    /**
     * 根据事件名称关键词匹配预置的警示语模板，AI不可用时作为回退。
     */
    private String buildFallbackText(String eventName, String verifyReason) {
        if (eventName != null) {
            for (Map.Entry<String, String> entry : FALLBACK_TEMPLATES.entrySet()) {
                if (eventName.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return "现场人员请注意，监控系统检测到" + (eventName != null ? eventName : "异常事件")
                + "，请相关人员立即核实并处理，确保作业安全。安全无小事，防范靠大家！";
    }
}
