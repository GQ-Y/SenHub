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
            "你是施工现场的安全员，刚刚通过监控发现了一起安全违规行为。\n" +
            "现在你要通过现场广播，像真人说话一样直接提醒违规人员。\n\n" +
            "【说话方式】\n" +
            "- 就像你站在现场，亲眼看到了违规，直接开口喊话\n" +
            "- 不要用'各位工友''现场人员请注意'这类广播套话，太生硬\n" +
            "- 用自然口语化的方式，比如'喂，那边那位''哎，安全帽呢'这种现场感\n" +
            "- 语气要有变化：可以严厉、可以关切、可以提醒，根据违规严重程度调整\n" +
            "- 可以适当描述你看到的场景细节（根据AI核验描述），让对方知道你确实在看着\n\n" +
            "【不同场景的说话风格参考】\n" +
            "- 安全帽/反光衣类：像看到自家兄弟忘了戴帽子一样提醒，关切但坚定\n" +
            "- 越界/入侵类：语气要严厉，有紧迫感，因为可能有生命危险\n" +
            "- 烟火/明火类：紧急警告，要果断有力\n" +
            "- 高处作业/攀爬类：语气严肃，强调后果\n" +
            "- 一般性违规：正常提醒语气，不用太紧张\n\n" +
            "【输出要求】\n" +
            "- 30~60字，适合语音播报，别太长\n" +
            "- 直接输出说话内容，不加引号和说明\n" +
            "- 每次表达方式要有变化，别重复固定句式\n" +
            "- 最后可以加一句简短的安全提醒，但不要每次都是同一句";

    private static final Map<String, String> FALLBACK_TEMPLATES = new LinkedHashMap<>();
    static {
        FALLBACK_TEMPLATES.put("反光衣",
                "喂，前面那位，反光衣没穿啊！这边车来车往的，看不见你多危险，赶紧把反光衣穿上。");
        FALLBACK_TEMPLATES.put("安全帽",
                "哎，安全帽呢？头顶上随时可能有东西掉下来，可不是闹着玩的，赶紧戴好。");
        FALLBACK_TEMPLATES.put("头盔",
                "那位，头盔怎么没戴？施工现场必须佩戴头盔，赶紧戴上再干活。");
        FALLBACK_TEMPLATES.put("越界",
                "停一下！这是危险区域，不能过去，赶紧退回来。里面正在作业，出了事可不是小事。");
        FALLBACK_TEMPLATES.put("入侵",
                "喂，那边的人，这里是禁区，马上出来！不要在危险区域逗留，抓紧撤出去。");
        FALLBACK_TEMPLATES.put("周界",
                "围栏外面那位，这里是警戒区域，不允许靠近。请立刻离开，注意自身安全。");
        FALLBACK_TEMPLATES.put("徘徊",
                "那边那位，在这一带转了好几圈了，这里是施工管控区域，没有作业任务请尽快离开。");
        FALLBACK_TEMPLATES.put("烟",
                "注意！现场好像有烟，所有人停下手上的活，先检查一下周围有没有火源，灭火器准备好。");
        FALLBACK_TEMPLATES.put("火",
                "紧急情况！发现明火，所有人立即撤离着火区域，灭火器赶紧拿过来，同时报告指挥部！");
        FALLBACK_TEMPLATES.put("摔倒",
                "有人摔倒了！旁边的兄弟赶紧过去看看，别慌，先别动伤者，确认情况再处理。");
        FALLBACK_TEMPLATES.put("聚集",
                "这一片人太多了，散开一点！保持间距，别都挤在一块，万一出事跑都跑不了。");
        FALLBACK_TEMPLATES.put("车辆",
                "那辆车停在那不行，挡住消防通道了。司机赶紧过来把车挪走，别影响安全通道。");
        FALLBACK_TEMPLATES.put("攀爬",
                "哎！别爬！这样上去太危险了，没有防护措施不能这么干。下来走正规通道上去。");
        FALLBACK_TEMPLATES.put("安全带",
                "上面那位，安全带系了没有？高处作业不系安全带拿命在赌，赶紧挂好再继续干。");
        FALLBACK_TEMPLATES.put("工装",
                "那位，工装怎么没穿？施工现场必须按规定着装，先去换好再过来。");
        FALLBACK_TEMPLATES.put("打电话",
                "哎，先把电话挂了！作业区不能打电话，分心太危险了，到外面安全的地方再打。");
        FALLBACK_TEMPLATES.put("吸烟",
                "把烟灭了！这是施工现场，严禁烟火，要抽到外面指定区域去。");
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
        return "喂，注意了！监控发现" + (eventName != null ? eventName : "异常情况")
                + "，相关人员赶紧处理一下，别等出了事才后悔。";
    }
}
