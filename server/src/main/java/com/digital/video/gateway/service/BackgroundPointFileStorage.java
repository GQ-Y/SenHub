package com.digital.video.gateway.service;

import com.digital.video.gateway.driver.livox.model.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 背景点云文件存储工具类
 * 
 * 存储格式：
 * - 文件扩展名：.pcd.gz（压缩的二进制格式）
 * - 文件头：4字节（点数量，int）
 * - 数据：每个点4个float（x, y, z, reflectivity），共16字节
 * - 使用GZIP压缩以减少存储空间
 */
public class BackgroundPointFileStorage {
    private static final Logger logger = LoggerFactory.getLogger(BackgroundPointFileStorage.class);
    
    private static final String STORAGE_DIR = "./storage/radar/backgrounds";
    private static final String FILE_EXTENSION = ".pcd.gz";
    
    static {
        // 确保存储目录存在
        try {
            Path dir = Paths.get(STORAGE_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                logger.info("创建背景点云存储目录: {}", STORAGE_DIR);
            }
        } catch (IOException e) {
            logger.error("创建存储目录失败", e);
        }
    }
    
    /**
     * 保存点云数据到文件
     * @param backgroundId 背景ID
     * @param points 点云数据列表
     * @return 文件路径
     */
    public static String savePoints(String backgroundId, List<Point> points) throws IOException {
        String fileName = backgroundId + FILE_EXTENSION;
        Path filePath = Paths.get(STORAGE_DIR, fileName);
        
        try (FileOutputStream fos = new FileOutputStream(filePath.toFile());
             GZIPOutputStream gzos = new GZIPOutputStream(fos);
             BufferedOutputStream bos = new BufferedOutputStream(gzos)) {
            
            // 写入点数量（4字节）
            ByteBuffer header = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            header.putInt(points.size());
            bos.write(header.array());
            
            // 写入点数据（每个点16字节：x, y, z, reflectivity，都是float）
            ByteBuffer buffer = ByteBuffer.allocate(points.size() * 16).order(ByteOrder.LITTLE_ENDIAN);
            for (Point point : points) {
                buffer.putFloat(point.x);
                buffer.putFloat(point.y);
                buffer.putFloat(point.z);
                buffer.putFloat(point.reflectivity != 0 ? point.reflectivity / 255.0f : 0.0f);
            }
            bos.write(buffer.array());
            
            bos.flush();
            logger.info("背景点云已保存到文件: {}, 点数: {}", filePath, points.size());
        }
        
        return filePath.toString();
    }
    
    /**
     * 从文件读取点云数据
     * @param backgroundId 背景ID
     * @param maxPoints 最大读取点数（用于采样，0表示读取全部）
     * @return 点云数据列表
     */
    public static List<Point> loadPoints(String backgroundId, int maxPoints) throws IOException {
        String fileName = backgroundId + FILE_EXTENSION;
        Path filePath = Paths.get(STORAGE_DIR, fileName);
        
        if (!Files.exists(filePath)) {
            throw new FileNotFoundException("背景点云文件不存在: " + filePath);
        }
        
        List<Point> points = new ArrayList<>();
        
        try (FileInputStream fis = new FileInputStream(filePath.toFile());
             GZIPInputStream gzis = new GZIPInputStream(fis);
             BufferedInputStream bis = new BufferedInputStream(gzis)) {
            
            // 读取点数量（4字节）
            byte[] headerBytes = new byte[4];
            int bytesRead = bis.read(headerBytes);
            if (bytesRead != 4) {
                throw new IOException("文件格式错误：无法读取点数量");
            }
            
            ByteBuffer header = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN);
            int pointCount = header.getInt();
            
            // 确定实际读取的点数
            int pointsToRead = (maxPoints > 0 && maxPoints < pointCount) ? maxPoints : pointCount;
            int step = (maxPoints > 0 && maxPoints < pointCount) ? pointCount / maxPoints : 1;
            
            // 读取点数据
            byte[] pointBytes = new byte[16];
            for (int i = 0; i < pointCount; i++) {
                bytesRead = bis.read(pointBytes);
                if (bytesRead != 16) {
                    break; // 文件结束
                }
                
                // 采样：只读取需要的点
                if (maxPoints > 0 && maxPoints < pointCount) {
                    if (i % step != 0) {
                        continue; // 跳过这个点
                    }
                }
                
                ByteBuffer pointBuffer = ByteBuffer.wrap(pointBytes).order(ByteOrder.LITTLE_ENDIAN);
                float x = pointBuffer.getFloat();
                float y = pointBuffer.getFloat();
                float z = pointBuffer.getFloat();
                float reflectivity = pointBuffer.getFloat();
                
                Point point = new Point(x, y, z, (byte)(reflectivity * 255));
                points.add(point);
                
                if (points.size() >= pointsToRead) {
                    break;
                }
            }
            
            logger.debug("从文件读取背景点云: {}, 原始点数: {}, 读取点数: {}", 
                    filePath, pointCount, points.size());
        }
        
        return points;
    }
    
    /**
     * 获取点云文件路径
     */
    public static String getFilePath(String backgroundId) {
        return Paths.get(STORAGE_DIR, backgroundId + FILE_EXTENSION).toString();
    }
    
    /**
     * 检查文件是否存在
     */
    public static boolean fileExists(String backgroundId) {
        Path filePath = Paths.get(STORAGE_DIR, backgroundId + FILE_EXTENSION);
        return Files.exists(filePath);
    }
    
    /**
     * 删除点云文件
     */
    public static boolean deleteFile(String backgroundId) {
        try {
            Path filePath = Paths.get(STORAGE_DIR, backgroundId + FILE_EXTENSION);
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                logger.info("删除背景点云文件: {}", filePath);
                return true;
            }
        } catch (IOException e) {
            logger.error("删除背景点云文件失败: {}", backgroundId, e);
        }
        return false;
    }
    
    /**
     * 获取文件大小（字节）
     */
    public static long getFileSize(String backgroundId) {
        try {
            Path filePath = Paths.get(STORAGE_DIR, backgroundId + FILE_EXTENSION);
            if (Files.exists(filePath)) {
                return Files.size(filePath);
            }
        } catch (IOException e) {
            logger.error("获取文件大小失败: {}", backgroundId, e);
        }
        return 0;
    }
}
