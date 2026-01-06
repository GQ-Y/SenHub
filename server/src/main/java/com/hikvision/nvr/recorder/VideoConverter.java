package com.hikvision.nvr.recorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 视频格式转换工具类
 * 使用FFmpeg将海康SDK录制的MPEG格式转换为浏览器可播放的FLV格式
 */
public class VideoConverter {
    private static final Logger logger = LoggerFactory.getLogger(VideoConverter.class);
    
    /**
     * 将MPEG格式的视频文件转换为FLV格式
     * @param inputFile 输入文件路径（MPEG格式）
     * @param outputFile 输出文件路径（FLV格式）
     * @return 转换是否成功
     */
    public static boolean convertToFlv(String inputFile, String outputFile) {
        File input = new File(inputFile);
        if (!input.exists()) {
            logger.error("输入文件不存在: {}", inputFile);
            return false;
        }
        
        try {
            // 构建FFmpeg命令
            // -i: 输入文件
            // -c:v copy: 视频流直接复制（不重新编码，速度快）
            // -c:a aac: 音频转换为AAC格式（FLV支持AAC）
            // -f flv: 输出格式为FLV
            // -y: 覆盖输出文件
            List<String> command = new ArrayList<>();
            command.add("ffmpeg");
            command.add("-i");
            command.add(inputFile);
            command.add("-c:v");
            command.add("copy");
            command.add("-c:a");
            command.add("aac");
            command.add("-b:a");
            command.add("64k");
            command.add("-f");
            command.add("flv");
            command.add("-y");
            command.add(outputFile);
            
            logger.info("开始转换视频: {} -> {}", inputFile, outputFile);
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // 读取输出（FFmpeg会将进度信息输出到stderr）
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                // 可以在这里解析FFmpeg的输出，显示转换进度
                if (line.contains("error") || line.contains("Error")) {
                    logger.warn("FFmpeg输出: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                File output = new File(outputFile);
                if (output.exists() && output.length() > 0) {
                    logger.info("视频转换成功: {} ({} bytes)", outputFile, output.length());
                    return true;
                } else {
                    logger.error("转换后的文件不存在或为空: {}", outputFile);
                    return false;
                }
            } else {
                logger.error("FFmpeg转换失败，退出码: {}", exitCode);
                return false;
            }
        } catch (Exception e) {
            logger.error("视频转换异常: {}", inputFile, e);
            return false;
        }
    }
    
    /**
     * 从视频文件中提取指定时间段的片段
     * @param inputFile 输入文件路径
     * @param outputFile 输出文件路径
     * @param startTime 开始时间（秒）
     * @param duration 持续时间（秒）
     * @return 提取是否成功
     */
    public static boolean extractSegment(String inputFile, String outputFile, double startTime, double duration) {
        File input = new File(inputFile);
        if (!input.exists()) {
            logger.error("输入文件不存在: {}", inputFile);
            return false;
        }
        
        try {
            // 构建FFmpeg命令
            // -ss: 开始时间
            // -t: 持续时间
            // -c:v copy: 视频流直接复制
            // -c:a aac: 音频转换为AAC
            // -f flv: 输出格式为FLV
            List<String> command = new ArrayList<>();
            command.add("ffmpeg");
            command.add("-ss");
            command.add(String.valueOf(startTime));
            command.add("-i");
            command.add(inputFile);
            command.add("-t");
            command.add(String.valueOf(duration));
            command.add("-c:v");
            command.add("copy");
            command.add("-c:a");
            command.add("aac");
            command.add("-b:a");
            command.add("64k");
            command.add("-f");
            command.add("flv");
            command.add("-y");
            command.add(outputFile);
            
            logger.info("开始提取视频片段: {} ({}秒开始，持续{}秒) -> {}", inputFile, startTime, duration, outputFile);
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("error") || line.contains("Error")) {
                    logger.warn("FFmpeg输出: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                File output = new File(outputFile);
                if (output.exists() && output.length() > 0) {
                    logger.info("视频片段提取成功: {} ({} bytes)", outputFile, output.length());
                    return true;
                } else {
                    logger.error("提取后的文件不存在或为空: {}", outputFile);
                    return false;
                }
            } else {
                logger.error("FFmpeg提取失败，退出码: {}", exitCode);
                return false;
            }
        } catch (Exception e) {
            logger.error("视频片段提取异常: {}", inputFile, e);
            return false;
        }
    }
    
    /**
     * 检查FFmpeg是否可用
     * @return FFmpeg是否可用
     */
    public static boolean isFfmpegAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            logger.warn("FFmpeg不可用: {}", e.getMessage());
            return false;
        }
    }
}
