package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowNodeDefinition;
import com.digital.video.gateway.workflow.FlowNodeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;

/**
 * 系统喇叭广播节点：通过系统音频设备直接播放 MP3 文件。
 * 优先播放工作流中 AI TTS 合成的音频，也可在节点配置中指定音频文件路径。
 * 在 Linux 上使用 aplay/mpv/ffplay 等命令播放。
 */
public class SystemSpeakerHandler implements FlowNodeHandler {
    private static final Logger logger = LoggerFactory.getLogger(SystemSpeakerHandler.class);

    private static final String[] PLAYERS = {"mpv", "ffplay", "aplay", "paplay"};

    @Override
    public boolean execute(FlowNodeDefinition node, FlowContext context) {
        String audioPath = resolveAudioPath(node, context);

        if (audioPath == null || audioPath.isBlank()) {
            logger.info("system_speaker: 无可播放的音频文件，跳过");
            return true;
        }

        File audioFile = new File(audioPath);
        if (!audioFile.exists()) {
            logger.warn("system_speaker: 音频文件不存在: {}", audioPath);
            return true;
        }

        logger.info("system_speaker: 开始播放音频: {}", audioPath);

        String player = findPlayer();
        if (player == null) {
            logger.warn("system_speaker: 未找到可用的音频播放器 (mpv/ffplay/aplay/paplay)");
            return true;
        }

        try {
            ProcessBuilder pb;
            if (player.equals("ffplay")) {
                pb = new ProcessBuilder(player, "-nodisp", "-autoexit", "-loglevel", "quiet", audioPath);
            } else if (player.equals("mpv")) {
                pb = new ProcessBuilder(player, "--no-video", "--really-quiet", audioPath);
            } else {
                pb = new ProcessBuilder(player, audioPath);
            }

            pb.redirectErrorStream(true);
            pb.inheritIO();
            Process process = pb.start();

            new Thread(() -> {
                try {
                    int exitCode = process.waitFor();
                    if (exitCode == 0) {
                        logger.info("system_speaker: 播放完成: {}", audioPath);
                    } else {
                        logger.warn("system_speaker: 播放器退出码={}, file={}", exitCode, audioPath);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("system_speaker: 播放被中断");
                }
            }, "SystemSpeaker-Play").start();

            return true;
        } catch (Exception e) {
            logger.error("system_speaker: 播放异常: {}", audioPath, e);
            return true;
        }
    }

    private String resolveAudioPath(FlowNodeDefinition node, FlowContext context) {
        // 优先使用节点配置的音频文件
        Map<String, Object> cfg = node.getConfig();
        if (cfg != null) {
            Object cfgPath = cfg.get("audioPath");
            if (cfgPath instanceof String && !((String) cfgPath).isBlank()) {
                return HandlerUtils.renderTemplate((String) cfgPath, context, null);
            }
        }

        // 其次使用 AI TTS 合成的音频
        if (context.getVariables() != null) {
            Object ttsPath = context.getVariables().get("ai_tts_audio_path");
            if (ttsPath instanceof String && !((String) ttsPath).isBlank()) {
                return (String) ttsPath;
            }
        }

        return null;
    }

    private String findPlayer() {
        for (String cmd : PLAYERS) {
            try {
                Process p = new ProcessBuilder("which", cmd)
                        .redirectErrorStream(true).start();
                int code = p.waitFor();
                if (code == 0) {
                    logger.debug("system_speaker: 使用播放器: {}", cmd);
                    return cmd;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
